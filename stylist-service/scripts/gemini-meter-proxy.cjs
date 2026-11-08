#!/usr/bin/env node
'use strict';

// Local-only HTTPS inspection proxy for the bounded Gemini verification run.
// It records operation counts, status, latency and token metadata only; it never
// persists request/response bodies, headers, API keys, prompts or model output.
const fs = require('node:fs');
const http = require('node:http');
const https = require('node:https');
const { buildUpstreamForward } = require('./gemini-meter-proxy-forwarding.cjs');
const {
  DEFAULT_LIMITS, limitsForProfile, classifyOperation, reserveAttempt, markDispatched, completeAttempt, failAttempt,
  safeProviderErrorDiagnostic, safeTokenCountRequestMetadata, readBoundedResponse
} = require('./gemini-meter-core.cjs');

const host = 'generativelanguage.googleapis.com';
const port = Number(process.env.STYLEMIND_GEMINI_METER_PORT || 0);
const keyPath = process.env.STYLEMIND_GEMINI_METER_KEY_FILE;
const certPath = process.env.STYLEMIND_GEMINI_METER_CERT_FILE;
const pfxPath = process.env.STYLEMIND_GEMINI_METER_PFX_FILE;
const pfxPassword = process.env.STYLEMIND_GEMINI_METER_PFX_PASSWORD;
const upstreamTimeoutMs = 30_000;
const meterProfile = process.env.STYLEMIND_GEMINI_METER_PROFILE || '';
const activeLimits = limitsForProfile(meterProfile);
if (pfxPath ? !pfxPassword : (!keyPath || !certPath)) {
  throw new Error('Local proxy certificate paths are required.');
}

const startedAt = new Date().toISOString();
const summary = {
  startedAt,
  calls: { metadata: 0, embedding: 0, tokenCount: 0, generation: 0, blocked: 0 },
  events: [],
  connectAttempts: 0,
  connectAccepted: 0,
  connectRejected: 0,
  tlsHandshakeErrors: 0,
  stoppedOn429: false
};
const seedFile = process.env.STYLEMIND_GEMINI_METER_SEED_FILE;
if (seedFile) {
  const seed = JSON.parse(fs.readFileSync(seedFile, 'utf8'));
  if (!seed.calls || !Array.isArray(seed.events)) throw new Error('Local meter seed is malformed.');
  Object.assign(summary.calls, seed.calls);
  summary.events.push(...seed.events);
  summary.connectAttempts = seed.connectAttempts || 0;
  summary.connectAccepted = seed.connectAccepted || 0;
  summary.connectRejected = seed.connectRejected || 0;
  summary.tlsHandshakeErrors = seed.tlsHandshakeErrors || 0;
  summary.stoppedOn429 = seed.stoppedOn429 === true;
}
for (const operation of ['embedding', 'tokenCount', 'generation']) {
  if (!Number.isSafeInteger(summary.calls[operation]) || summary.calls[operation] < 0
      || summary.calls[operation] > activeLimits[operation]) {
    throw new Error('Local meter seed exceeds the selected cumulative limit.');
  }
}
if (summary.stoppedOn429) throw new Error('Local meter seed records HTTP 429; live forwarding is disabled.');
let halted = summary.stoppedOn429;

function safeEmbeddingRequestMetadata(parsed, bytes) {
  let payload;
  try { payload = JSON.parse(bytes.toString('utf8')); } catch (_) { return { valid: false }; }
  const entries = parsed.endpointAction === 'batchEmbedContents' ? payload?.requests : [payload];
  if (!Array.isArray(entries) || entries.length !== 1) return { valid: false };
  const request = entries[0];
  const config = parsed.endpointAction === 'batchEmbedContents' ? request : request?.embedContentConfig;
  const content = request?.content;
  const text = content?.parts?.length === 1 && typeof content.parts[0]?.text === 'string'
    ? content.parts[0].text : null;
  const taskType = config?.taskType;
  const requestedDimensions = config?.outputDimensionality;
  const topFields = Object.keys(payload).sort();
  const itemFields = Object.keys(request || {}).sort();
  const configFields = parsed.endpointAction === 'embedContent'
    ? Object.keys(config || {}).sort() : [];
  const valid = request?.model === `models/${parsed.model}`
    && text !== null
    && ['RETRIEVAL_QUERY', 'RETRIEVAL_DOCUMENT'].includes(taskType)
    && requestedDimensions === 1_536
    && !Object.hasOwn(payload, 'autoTruncate')
    && !Object.hasOwn(request || {}, 'autoTruncate')
    && !Object.hasOwn(config || {}, 'autoTruncate')
    && (parsed.endpointAction !== 'embedContent'
      || (topFields.join(',') === 'content,embedContentConfig,model'
        && configFields.join(',') === 'outputDimensionality,taskType'))
    && (parsed.endpointAction !== 'batchEmbedContents'
      || (topFields.join(',') === 'requests'
        && itemFields.join(',') === 'content,model,outputDimensionality,taskType'));
  return {
    valid,
    endpointAction: parsed.endpointAction,
    taskType: typeof taskType === 'string' ? taskType : null,
    requestedDimensions: Number.isInteger(requestedDimensions) ? requestedDimensions : null,
    inputCharacters: text === null ? null : [...text].length,
    fields: parsed.endpointAction === 'embedContent'
      ? ['model', 'content', 'embedContentConfig.taskType', 'embedContentConfig.outputDimensionality']
      : ['requests[0].model', 'requests[0].content', 'requests[0].taskType', 'requests[0].outputDimensionality'],
    inputCount: entries.length
  };
}

function jsonResponse(response, status, body) {
  response.writeHead(status, { 'content-type': 'application/json', 'connection': 'close' });
  response.end(JSON.stringify(body));
}

function record(event) {
  summary.events.push(event);
}

function readBody(request, maxBytes = 1024 * 1024) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let length = 0;
    request.on('data', chunk => {
      length += chunk.length;
      if (length > maxBytes) {
        reject(new Error('request-too-large'));
        request.destroy();
      } else chunks.push(chunk);
    });
    request.on('end', () => resolve(Buffer.concat(chunks)));
    request.on('error', () => reject(new Error('request-read-failed')));
  });
}

const tlsOptions = pfxPath
  ? { pfx: fs.readFileSync(pfxPath), passphrase: pfxPassword }
  : { key: fs.readFileSync(keyPath), cert: fs.readFileSync(certPath) };
const secureServer = https.createServer(tlsOptions, async (request, response) => {
  const started = Date.now();
  let parsed;
  let event;
  try {
    const url = new URL(request.url, `https://${request.headers.host || host}`);
    if (url.hostname !== host || url.searchParams.has('key') || url.searchParams.has('api_key')) {
      return jsonResponse(response, 403, { error: { status: 'PERMISSION_DENIED' } });
    }
    parsed = classifyOperation(request.method, url.pathname);
    if (!parsed) return jsonResponse(response, 403, { error: { status: 'PERMISSION_DENIED' } });
    if (halted && parsed.operation !== 'metadata') {
      summary.calls.blocked++;
      record({ operation: parsed.operation, model: parsed.model, status: 429, blocked: true, latencyMs: 0 });
      return jsonResponse(response, 429, { error: { status: 'RESOURCE_EXHAUSTED' } });
    }
    if (summary.stoppedOn429) {
      summary.calls.blocked++;
      record({ operation: parsed.operation, model: parsed.model, status: 429, blocked: true, latencyMs: 0 });
      return jsonResponse(response, 429, { error: { status: 'RESOURCE_EXHAUSTED' } });
    }
    const body = request.method === 'GET' || request.method === 'HEAD' ? undefined : await readBody(request);
    const requestMetadata = parsed.operation === 'embedding'
      ? safeEmbeddingRequestMetadata(parsed, body || Buffer.alloc(0))
      : parsed.operation === 'tokenCount'
        ? safeTokenCountRequestMetadata(parsed, body || Buffer.alloc(0)) : null;
    if (parsed.operation === 'embedding' && !requestMetadata.valid) {
      summary.calls.blocked++;
      record({ operation: parsed.operation, model: parsed.model, endpointAction: parsed.endpointAction,
        status: 400, blocked: true, requestShapeValid: false, latencyMs: Date.now() - started });
      return jsonResponse(response, 400, { error: { status: 'INVALID_SYNTHETIC_PROBE_SHAPE' } });
    }
    if (parsed.operation === 'tokenCount' && !requestMetadata.valid) {
      summary.calls.blocked++;
      record({ operation: parsed.operation, model: parsed.model, status: 400,
        blocked: true, requestShapeValid: false, latencyMs: Date.now() - started });
      return jsonResponse(response, 400, { error: { status: 'INVALID_SYNTHETIC_COUNT_TOKENS_SHAPE' } });
    }
    const eventLimits = Object.assign({}, activeLimits, { metadata: null });
    event = reserveAttempt(summary, parsed.operation, parsed.model, eventLimits);
    if (!event) {
      summary.calls.blocked++;
      record({ operation: parsed.operation, model: parsed.model, status: 429, blocked: true, latencyMs: 0 });
      return jsonResponse(response, 429, { error: { status: 'RESOURCE_EXHAUSTED' } });
    }
    if (parsed.operation === 'embedding') Object.assign(event, {
      requestShapeValid: requestMetadata.valid,
      endpointAction: requestMetadata.endpointAction,
      taskType: requestMetadata.taskType,
      requestedDimensions: requestMetadata.requestedDimensions,
      inputCharacters: requestMetadata.inputCharacters,
      requestFields: requestMetadata.fields,
      inputCount: requestMetadata.inputCount
    });
    if (parsed.operation === 'tokenCount') Object.assign(event, {
      requestShapeValid: requestMetadata.valid,
      requestKind: requestMetadata.requestKind,
      requestFields: requestMetadata.requestFields,
      generationConfigFields: requestMetadata.generationConfigFields
    });
    const forward = buildUpstreamForward(request, url, host, body, AbortSignal.timeout(upstreamTimeoutMs));
    // Reserve is persisted in memory before dispatch. Any upstream HTTP error, timeout or
    // connection failure remains charged to the cumulative local cap.
    markDispatched(event);
    const upstream = await fetch(forward.url, forward.options);
    const boundedBody = await readBoundedResponse(upstream,
      upstream.status >= 400 ? 64 * 1024 : 256 * 1024);
    const bytes = boundedBody.bytes;
    let bodyJson = null;
    try { bodyJson = JSON.parse(bytes.toString('utf8')); } catch (_) { }
    completeAttempt(event, upstream.status, Date.now() - started);
    if (upstream.status >= 400) {
      event.providerError = safeProviderErrorDiagnostic(upstream.status, bytes, boundedBody.truncated);
    } else if (boundedBody.truncated) {
      event.responseTruncated = true;
    }
    if (parsed.operation === 'tokenCount' && Number.isInteger(bodyJson?.totalTokens)) {
      event.inputTokens = bodyJson.totalTokens;
    }
    if (parsed.operation === 'embedding') {
      const embedding = parsed.endpointAction === 'batchEmbedContents'
        ? bodyJson?.embeddings?.[0] : bodyJson?.embedding;
      const values = embedding?.values;
      event.responseKeys = bodyJson && typeof bodyJson === 'object' ? Object.keys(bodyJson) : [];
      event.embeddingObjectKeys = embedding && typeof embedding === 'object' ? Object.keys(embedding) : [];
      event.embeddingDimensions = Array.isArray(values) ? values.length : null;
      event.embeddingValuesNumeric = Array.isArray(values) && values.every(value => Number.isFinite(value));
      event.rawVectorL2Norm = Array.isArray(values) && values.every(value => Number.isFinite(value))
        ? Math.sqrt(values.reduce((sum, value) => sum + value * value, 0)) : null;
      const embeddings = bodyJson?.embeddings;
      event.embeddingsArrayCount = Array.isArray(embeddings) ? embeddings.length : null;
      event.firstEmbeddingsDimensions = Array.isArray(embeddings?.[0]?.values) ? embeddings[0].values.length : null;
    }
    if (parsed.operation === 'generation' && bodyJson?.usageMetadata) {
      event.promptTokens = Number.isInteger(bodyJson.usageMetadata.promptTokenCount)
        ? bodyJson.usageMetadata.promptTokenCount : null;
      event.outputTokens = Number.isInteger(bodyJson.usageMetadata.candidatesTokenCount)
        ? bodyJson.usageMetadata.candidatesTokenCount : null;
      event.thoughtTokens = Number.isInteger(bodyJson.usageMetadata.thoughtsTokenCount)
        ? bodyJson.usageMetadata.thoughtsTokenCount : null;
    }
    if (upstream.status === 429) {
      halted = true;
      summary.stoppedOn429 = true;
    }
    const responseHeaders = {};
    for (const [name, value] of upstream.headers) {
      if (['connection', 'transfer-encoding', 'content-length', 'content-encoding'].includes(name.toLowerCase())) continue;
      responseHeaders[name] = value;
    }
    response.writeHead(upstream.status, responseHeaders);
    response.end(bytes);
  } catch (error) {
    // Only a generic error label is retained; never serialize URLs, headers or bodies.
    if (event) failAttempt(event, error, Date.now() - started);
    else record({ operation: parsed?.operation || 'unknown', model: parsed?.model || null,
      status: 502, error: ['TimeoutError', 'AbortError', 'TypeError', 'FetchError'].includes(error.name)
        ? error.name : 'ProviderTransportError', latencyMs: Date.now() - started });
    jsonResponse(response, 502, { error: { status: 'BAD_GATEWAY' } });
  }
});

const proxy = http.createServer((request, response) => {
  if (request.method === 'GET' && request.url === '/__summary') {
    return jsonResponse(response, 200, summary);
  }
  if (request.method === 'GET' && request.url === '/__ready') {
    return jsonResponse(response, 200, {
      ready: true,
      calls: summary.calls,
      limits: activeLimits,
      profile: meterProfile || 'DEFAULT',
      upstreamTimeoutMs,
      stoppedOn429: summary.stoppedOn429
    });
  }
  jsonResponse(response, 404, { error: { status: 'NOT_FOUND' } });
});

secureServer.on('tlsClientError', error => {
  summary.tlsHandshakeErrors++;
  record({ operation: 'tlsHandshake', status: 0, error: error.code || error.name || 'TLS_ERROR' });
});

proxy.on('connect', (request, socket, head) => {
  summary.connectAttempts++;
  if (request.url !== `${host}:443`) {
    summary.connectRejected++;
    record({ operation: 'connect', status: 403, blocked: true });
    socket.end('HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n');
    return;
  }
  summary.connectAccepted++;
  socket.write('HTTP/1.1 200 Connection Established\r\nProxy-Agent: StyleMind-Local-Smoke\r\n\r\n');
  if (head?.length) socket.unshift(head);
  secureServer.emit('connection', socket);
});

proxy.listen(port, '127.0.0.1', () => {
  process.stdout.write(JSON.stringify({ status: 'ready', port: proxy.address().port }) + '\n');
});
process.on('SIGINT', () => proxy.close(() => process.exit(0)));
process.on('SIGTERM', () => proxy.close(() => process.exit(0)));
