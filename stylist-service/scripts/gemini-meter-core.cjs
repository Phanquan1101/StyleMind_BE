'use strict';

const DEFAULT_LIMITS = Object.freeze({ embedding: 10, tokenCount: 20, generation: 5 });
const APPROVED_FINAL_GATEWAY_PROFILE = 'EP07_FINAL_GATEWAY_20261107';
const APPROVED_FINAL_GATEWAY_LIMITS = Object.freeze({ embedding: 12, tokenCount: 23, generation: 6 });
const MAX_DIAGNOSTIC_BYTES = 64 * 1024;

function limitsForProfile(profile) {
  if (profile == null || profile === '') return DEFAULT_LIMITS;
  if (profile === APPROVED_FINAL_GATEWAY_PROFILE) return APPROVED_FINAL_GATEWAY_LIMITS;
  throw new Error('meter-profile-unsupported');
}

function classifyOperation(method, pathname) {
  const match = pathname.match(/\/v1beta\/models\/([^/:]+):([^/]+)$/);
  if (!match) return method === 'GET' && pathname === '/v1beta/models'
    ? { operation: 'metadata', model: null } : null;
  const model = match[1];
  const action = match[2];
  if (action === 'countTokens' || action === 'countTextTokens') return { operation: 'tokenCount', model };
  if (action === 'embedContent' || action === 'batchEmbedContents') {
    return { operation: 'embedding', model, endpointAction: action };
  }
  if (action === 'generateContent') return { operation: 'generation', model };
  return null;
}

function reserveAttempt(summary, operation, model, limits = DEFAULT_LIMITS) {
  if (!summary || !summary.calls || !Array.isArray(summary.events)
      || !Object.hasOwn(summary.calls, operation)) throw new Error('meter-summary-invalid');
  const limit = limits[operation];
  if (Number.isInteger(limit) && summary.calls[operation] >= limit) return null;
  summary.calls[operation] += 1;
  const event = { operation, model, status: null, reserved: true, dispatched: false };
  summary.events.push(event);
  return event;
}

function markDispatched(event) {
  if (!event || event.reserved !== true) throw new Error('meter-attempt-not-reserved');
  event.dispatched = true;
}

function completeAttempt(event, status, latencyMs) {
  if (!event || event.reserved !== true) throw new Error('meter-attempt-not-reserved');
  event.status = status;
  event.latencyMs = Math.max(0, Math.trunc(latencyMs));
}

function failAttempt(event, error, latencyMs) {
  if (!event || event.reserved !== true) throw new Error('meter-attempt-not-reserved');
  event.status = 502;
  event.error = safeTransportErrorName(error);
  event.latencyMs = Math.max(0, Math.trunc(latencyMs));
}

function safeTransportErrorName(error) {
  const name = typeof error?.name === 'string' ? error.name : 'ProviderTransportError';
  return ['TimeoutError', 'AbortError', 'TypeError', 'FetchError'].includes(name) ? name : 'ProviderTransportError';
}

function safeProviderErrorDiagnostic(httpStatus, body, truncated = false) {
  const safe = { httpStatus };
  if (truncated) safe.bodyTruncated = true;
  if (!Buffer.isBuffer(body) || body.length > MAX_DIAGNOSTIC_BYTES) return safe;
  let parsed;
  try { parsed = JSON.parse(body.toString('utf8')); } catch (_) { return safe; }
  const error = parsed && typeof parsed === 'object' ? parsed.error : null;
  if (!error || typeof error !== 'object') return safe;
  if (Number.isSafeInteger(error.code)) safe.code = error.code;
  if (typeof error.status === 'string' && /^[A-Z][A-Z0-9_]{0,39}$/.test(error.status)) safe.status = error.status;
  const fieldPaths = [];
  for (const detail of Array.isArray(error.details) ? error.details : []) {
    const violations = Array.isArray(detail?.fieldViolations) ? detail.fieldViolations : [];
    for (const violation of violations) {
      const path = violation?.field;
      if (typeof path === 'string' && /^[A-Za-z0-9_.\[\]-]{1,128}$/.test(path)) {
        fieldPaths.push(path);
        if (fieldPaths.length >= 16) break;
      }
    }
    if (fieldPaths.length >= 16) break;
  }
  if (fieldPaths.length) safe.fieldPaths = [...new Set(fieldPaths)];
  return safe;
}

function safeTokenCountRequestMetadata(parsed, bytes) {
  let payload;
  try { payload = JSON.parse(bytes.toString('utf8')); } catch (_) { return { valid: false }; }
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) return { valid: false };
  const hasContents = Array.isArray(payload.contents);
  const hasGenerateRequest = payload.generateContentRequest && typeof payload.generateContentRequest === 'object';
  if (hasContents === Boolean(hasGenerateRequest)) return { valid: false };
  if (hasContents) {
    const hasText = payload.contents.length > 0 && payload.contents.every(content =>
      Array.isArray(content?.parts) && content.parts.length > 0
      && content.parts.every(part => typeof part?.text === 'string' && part.text.trim().length > 0));
    return {
      valid: hasText,
      requestKind: 'contents',
      model: parsed.model,
      requestFields: ['contents', 'contents[].parts[].text', 'contents[].role']
    };
  }
  const request = payload.generateContentRequest;
  const hasSystemText = Array.isArray(request.systemInstruction?.parts)
    && request.systemInstruction.parts.some(part => typeof part?.text === 'string' && part.text.trim().length > 0);
  const hasUserText = Array.isArray(request.contents) && request.contents.length > 0
    && request.contents.every(content => Array.isArray(content?.parts)
      && content.parts.some(part => typeof part?.text === 'string' && part.text.trim().length > 0));
  const requestFields = ['generateContentRequest.model', 'generateContentRequest.systemInstruction',
    'generateContentRequest.contents'];
  const config = request.generationConfig;
  const generationConfigFields = [];
  if (config && typeof config === 'object' && !Array.isArray(config)) {
    for (const field of Object.keys(config).sort()) generationConfigFields.push(`generateContentRequest.generationConfig.${field}`);
  }
  return {
    valid: request.model === `models/${parsed.model}` && hasSystemText && hasUserText,
    requestKind: 'generateContentRequest',
    model: request.model === `models/${parsed.model}` ? request.model : null,
    requestFields,
    generationConfigFields
  };
}

async function readBoundedResponse(response, maxBytes) {
  if (!response.body) return { bytes: Buffer.alloc(0), truncated: false };
  const reader = response.body.getReader();
  const chunks = [];
  let length = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      const chunk = Buffer.from(value);
      const remaining = maxBytes - length;
      if (chunk.length > remaining) {
        if (remaining > 0) chunks.push(chunk.subarray(0, remaining));
        await reader.cancel().catch(() => {});
        return { bytes: Buffer.concat(chunks), truncated: true };
      }
      chunks.push(chunk);
      length += chunk.length;
    }
    return { bytes: Buffer.concat(chunks), truncated: false };
  } finally {
    reader.releaseLock();
  }
}

module.exports = {
  DEFAULT_LIMITS,
  APPROVED_FINAL_GATEWAY_PROFILE,
  APPROVED_FINAL_GATEWAY_LIMITS,
  limitsForProfile,
  MAX_DIAGNOSTIC_BYTES,
  classifyOperation,
  reserveAttempt,
  markDispatched,
  completeAttempt,
  failAttempt,
  safeProviderErrorDiagnostic,
  safeTokenCountRequestMetadata,
  safeTransportErrorName,
  readBoundedResponse
};
