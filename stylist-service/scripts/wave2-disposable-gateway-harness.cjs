#!/usr/bin/env node
'use strict';

/*
 * Disposable Wave 2 HTTP acceptance harness.
 * Starts only named --rm containers on random host ports, local contract fixtures,
 * the already-built Stylist/Gateway jars, then runs the checked-in Newman collection.
 * No shared Compose project, persistent volume, live Commerce, Identity or paid provider.
 */
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const crypto = require('node:crypto');
const http = require('node:http');
const net = require('node:net');
const { spawn, execFile } = require('node:child_process');
const { isDeepStrictEqual, promisify } = require('node:util');
const {
  compareGeneratedWithDetail,
  compareGeneratedWithRequestRead,
  compareGenerationReplay
} = require('./wave2-recommendation-response-assertions.cjs');
const {
  APPROVED_FINAL_GATEWAY_PROFILE,
  limitsForProfile
} = require('./gemini-meter-core.cjs');

const execFileAsync = promisify(execFile);
const root = path.resolve(__dirname, '..', '..');
const stylistRoot = path.resolve(__dirname, '..');
const evidenceDir = process.env.STYLEMIND_WAVE2_EVIDENCE_DIR
  ? path.resolve(process.env.STYLEMIND_WAVE2_EVIDENCE_DIR)
  : path.join(stylistRoot, 'target', 'wave2-disposable-gateway');
const collectionPath = path.join(stylistRoot, 'postman', 'StyleMind-Stylist-Wave2.postman_collection.json');
const serviceJar = path.join(stylistRoot, 'target', 'stylist-service-0.0.1-SNAPSHOT.jar');
const gatewayJar = path.join(root, 'api-gateway', 'target', 'api-gateway-0.0.1-SNAPSHOT.jar');
const pgUser = 'wave2_disposable';
const pgPassword = 'wave2-disposable-only';
const neoPassword = 'wave2-neo4j-disposable-only';
const fixtureKey = 'local-fixture-key-only';
const geminiFixtureKey = 'offline-gemini-http-fixture';
// This offline verification is deliberately pinned to the exact candidate
// model under review. The fixture rejects any other generation model.
const geminiGenerationModel = 'gemini-3.5-flash-lite';
const geminiMode = process.env.STYLEMIND_AI_PROVIDER === 'gemini';
const geminiMockMode = geminiMode && process.env.STYLEMIND_GEMINI_MOCK_ENABLED === 'true';
const geminiLiveMode = geminiMode && !geminiMockMode;
const geminiMockPositiveFlowOnly = geminiMockMode
  && process.env.STYLEMIND_GEMINI_MOCK_POSITIVE_FLOW_ONLY === 'true';
const boundedPositiveFlowMode = geminiLiveMode || geminiMockPositiveFlowOnly;
const names = ['pg', 'qdrant', 'neo4j'].map(function (suffix) {
  return 'stylemind-wave2-' + process.pid + '-' + crypto.randomBytes(4).toString('hex') + '-' + suffix;
});
const children = [];
const servers = [];
const tokens = [];
const outputBuffers = new Map();
const childLabels = new Map();
if (geminiLiveMode && process.env.STYLEMIND_GEMINI_API_KEY) {
  tokens.push(process.env.STYLEMIND_GEMINI_API_KEY);
}

function localChildEnv() {
  const env = Object.assign({}, process.env);
  delete env.STYLEMIND_GEMINI_API_KEY;
  return env;
}

function execLocalFile(command, args, options) {
  return execFileAsync(command, args, Object.assign({ env: localChildEnv() }, options || {}));
}

const ids = {
  topCategory: '61000000-0000-4000-8000-000000000001',
  bottomCategory: '61000000-0000-4000-8000-000000000002',
  topProduct: '62000000-0000-4000-8000-000000000001',
  bottomProduct: '62000000-0000-4000-8000-000000000002',
  topVariant: '63000000-0000-4000-8000-000000000001',
  bottomVariant: '63000000-0000-4000-8000-000000000002',
  brand: '64000000-0000-4000-8000-000000000001'
};
const commerceProducts = [
  product(ids.topProduct, ids.topVariant, ids.topCategory, 'Áo sơ mi trắng', 'TOP', 250000, 'S', 'trắng', null, null),
  product(ids.bottomProduct, ids.bottomVariant, ids.bottomCategory, 'Quần linen đen', 'BOTTOM', 300000, 'M', 'đen', 'linen', null)
];

let openAiCounts = { embeddings: 0, tokenCounts: 0, generations: 0 };
const geminiMeterUrl = process.env.STYLEMIND_GEMINI_METER_URL || '';
const geminiMeterProfile = process.env.STYLEMIND_GEMINI_METER_PROFILE || '';
const geminiMeterLimits = limitsForProfile(geminiMeterProfile);
let geminiMockCounts = null;
let geminiMockGenerationRequests = null;
let heldChat = false;
let chatEnteredResolve = null;
let chatReleaseResolve = null;
let chatEntered = Promise.resolve();
let chatRelease = Promise.resolve();
let nextJwks = null;

function product(productId, variantId, categoryId, name, slot, price, size, color, material, description) {
  const variant = {
    variant_id: variantId,
    product_id: productId,
    sku: 'WAVE2-' + slot,
    size: size,
    color: color,
    material: material,
    unit_price_vnd: price,
    variant_status: 'ACTIVE',
    images: [{
      image_id: variantId === ids.topVariant
        ? '65000000-0000-4000-8000-000000000001'
        : '65000000-0000-4000-8000-000000000002',
      variant_id: variantId,
      image_url: 'https://images.synthetic.test/' + variantId + '.jpg',
      alt_text: 'Synthetic test garment',
      display_order: 0,
      is_primary: true
    }]
  };
  return {
    product_id: productId,
    category_id: categoryId,
    brand_id: ids.brand,
    category_name: 'Synthetic Women ' + slot,
    brand_name: 'Synthetic Test Brand',
    name: name,
    description: description,
    demographic_segment: 'WOMEN',
    product_status: 'ACTIVE',
    variants: [variant]
  };
}

function redact(value) {
  let result = String(value);
  for (const token of tokens) if (token) result = result.split(token).join('[REDACTED_TOKEN]');
  result = result.split(fixtureKey).join('[REDACTED_FIXTURE_KEY]');
  result = result.split(geminiFixtureKey).join('[REDACTED_GEMINI_FIXTURE_KEY]');
  result = result.replace(/Bearer\s+[A-Za-z0-9._~-]+/g, 'Bearer [REDACTED]');
  result = result.replace(/-----BEGIN [A-Z ]+-----[\s\S]*?-----END [A-Z ]+-----/g, '[REDACTED_PRIVATE_KEY]');
  return result;
}

function writeRedacted(file, content) {
  fs.writeFileSync(file, redact(content), { encoding: 'utf8', mode: 0o600 });
}

function spawnLogged(command, args, label, env) {
  const childEnv = Object.assign({}, process.env, env || {});
  if (label !== 'stylist' || !geminiLiveMode) delete childEnv.STYLEMIND_GEMINI_API_KEY;
  delete childEnv.STYLEMIND_GEMINI_METER_TRUSTSTORE_PASSWORD;
  const child = spawn(command, args, {
    cwd: root,
    env: childEnv,
    windowsHide: true,
    stdio: ['ignore', 'pipe', 'pipe']
  });
  children.push(child);
  childLabels.set(child, label);
  outputBuffers.set(child, '');
  const logName = label.replace(/[^A-Za-z0-9._-]/g, '_');
  const logPath = path.join(evidenceDir, logName + '.redacted.log');
  fs.writeFileSync(logPath, '', { encoding: 'utf8', mode: 0o600 });
  for (const stream of [child.stdout, child.stderr]) {
    stream.on('data', function (chunk) {
      const current = outputBuffers.get(child) || '';
      const updated = (current + chunk.toString('utf8')).slice(-500000);
      outputBuffers.set(child, updated);
      try { writeRedacted(logPath, updated); } catch (_) { }
    });
  }
  child.on('error', function (error) {
    const current = outputBuffers.get(child) || '';
    outputBuffers.set(child, current + '\n' + label + ' process error: ' + error.message);
  });
  return child;
}

function javaTruststoreArgs() {
  if (!geminiLiveMode) return [];
  const truststore = process.env.STYLEMIND_GEMINI_METER_TRUSTSTORE;
  const password = process.env.STYLEMIND_GEMINI_METER_TRUSTSTORE_PASSWORD;
  if (!truststore && !password) return [];
  if (!truststore || !password) throw new Error('Gemini meter truststore configuration is incomplete.');
  if (!tokens.includes(password)) tokens.push(password);
  return [
    '-Djavax.net.ssl.trustStore=' + path.resolve(truststore),
    '-Djavax.net.ssl.trustStorePassword=' + password
  ];
}

function waitProcess(child, timeoutMs, label) {
  return new Promise(function (resolve, reject) {
    const timer = setTimeout(function () {
      reject(new Error(label + ' timed out'));
    }, timeoutMs);
    child.once('error', function (error) {
      clearTimeout(timer);
      reject(error);
    });
    child.once('exit', function (code, signal) {
      clearTimeout(timer);
      resolve({ code: code, signal: signal });
    });
  });
}

function listen(server) {
  return new Promise(function (resolve, reject) {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', function () {
      server.removeListener('error', reject);
      servers.push(server);
      resolve(server.address().port);
    });
  });
}

async function readJson(request) {
  const chunks = [];
  for await (const chunk of request) chunks.push(chunk);
  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}

function sendJson(response, status, body) {
  const bytes = Buffer.from(JSON.stringify(body));
  response.writeHead(status, { 'content-type': 'application/json', 'content-length': bytes.length });
  response.end(bytes);
}

function jsonDifferencePaths(expected, actual, pathName, result) {
  const output = result || [];
  const currentPath = pathName || '$';
  if (output.length >= 20) return output;
  if (Array.isArray(expected) || Array.isArray(actual)) {
    if (!Array.isArray(expected) || !Array.isArray(actual)) {
      output.push(currentPath);
      return output;
    }
    if (expected.length !== actual.length) output.push(currentPath + '.length');
    for (let index = 0; index < Math.min(expected.length, actual.length) && output.length < 20; index++) {
      jsonDifferencePaths(expected[index], actual[index], currentPath + '[' + index + ']', output);
    }
    return output;
  }
  const expectedObject = expected !== null && typeof expected === 'object';
  const actualObject = actual !== null && typeof actual === 'object';
  if (expectedObject || actualObject) {
    if (!expectedObject || !actualObject) {
      output.push(currentPath);
      return output;
    }
    const keys = Array.from(new Set(Object.keys(expected).concat(Object.keys(actual)))).sort();
    for (const key of keys) {
      if (output.length >= 20) break;
      if (!Object.prototype.hasOwnProperty.call(expected, key)
          || !Object.prototype.hasOwnProperty.call(actual, key)) {
        output.push(currentPath + '.' + key);
      } else {
        jsonDifferencePaths(expected[key], actual[key], currentPath + '.' + key, output);
      }
    }
    return output;
  }
  if (!Object.is(expected, actual)) output.push(currentPath);
  return output;
}

function newmanResponseJson(newmanReport, itemName) {
  const execution = newmanReport?.run?.executions?.find(entry => entry.item?.name === itemName);
  const responseBytes = execution?.response?.stream?.data;
  if (!Array.isArray(responseBytes)) {
    throw new Error('Newman response evidence is missing for ' + itemName);
  }
  try {
    return JSON.parse(Buffer.from(responseBytes).toString('utf8'));
  } catch (_) {
    throw new Error('Newman response evidence is invalid JSON for ' + itemName);
  }
}

function parsePath(url) {
  return new URL(url, 'http://127.0.0.1').pathname;
}

function commerceFixture() {
  const byVariant = new Map();
  const byProduct = new Map();
  for (const item of commerceProducts) {
    byProduct.set(item.product_id, item);
    for (const variant of item.variants) byVariant.set(variant.variant_id, variant);
  }
  return http.createServer(async function (request, response) {
    try {
      const url = new URL(request.url, 'http://commerce.fixture');
      if (request.method === 'GET' && url.pathname === '/api/v1/products') {
        const page = Number(url.searchParams.get('page'));
        const size = Number(url.searchParams.get('size'));
        const demographic = url.searchParams.get('demographic_segment');
        if (demographic !== 'WOMEN' || page !== 0 || size !== 100) return sendJson(response, 400, {});
        return sendJson(response, 200, {
          items: commerceProducts,
          page: 0,
          size: 100,
          total_elements: commerceProducts.length,
          total_pages: 1
        });
      }
      if (request.method === 'POST' && url.pathname === '/api/v1/variants/batch-lookup') {
        const body = await readJson(request);
        const requested = Array.isArray(body.variant_ids) ? body.variant_ids : [];
        return sendJson(response, 200, {
          items: requested.map(function (id) { return byVariant.get(id); }).filter(Boolean)
        });
      }
      const match = request.method === 'GET' && url.pathname.match(/^\/api\/v1\/products\/([0-9a-f-]+)$/i);
      if (match) {
        const result = byProduct.get(match[1]);
        return sendJson(response, result ? 200 : 404, result || {});
      }
      return sendJson(response, 404, {});
    } catch (_) {
      return sendJson(response, 400, {});
    }
  });
}

function openAiFixture() {
  return http.createServer(async function (request, response) {
    try {
      const urlPath = parsePath(request.url);
      if (request.method !== 'POST') return sendJson(response, 404, {});
      const body = await readJson(request);
      if (urlPath === '/v1/embeddings') {
        openAiCounts.embeddings++;
        const count = Array.isArray(body.input) ? body.input.length : 1;
        return sendJson(response, 200, {
          object: 'list',
          data: Array.from({ length: count }, function (_, index) {
            const embedding = Array(1536).fill(0.001);
            embedding[0] = 1;
            return { object: 'embedding', index: index, embedding: embedding };
          }),
          model: 'text-embedding-3-small',
          usage: { prompt_tokens: count, total_tokens: count }
        });
      }
      if (urlPath === '/v1/responses/input_tokens') {
        openAiCounts.tokenCounts++;
        const inputText = typeof body.input === 'string' ? body.input : JSON.stringify(body.input);
        const overBudget = inputText.includes('á'.repeat(2000));
        return sendJson(response, 200, {
          object: 'response.input_tokens',
          input_tokens: overBudget ? 6001 : 1200
        });
      }
      if (urlPath === '/v1/responses') {
        openAiCounts.generations++;
        if (heldChat) {
          if (chatEnteredResolve) chatEnteredResolve();
          await chatRelease;
          heldChat = false;
        }
        const input = JSON.parse(body.input);
        const outfit = input.complete_outfit_choices[0];
        if (!outfit || !Array.isArray(outfit.items) || outfit.items.length !== 2) {
          return sendJson(response, 200, {
            id: 'resp-synthetic-invalid',
            status: 'completed',
            output: [{ type: 'message', content: [{ type: 'output_text', text: '{"recommendations":[]}' }] }]
          });
        }
        const content = {
          recommendations: [{
            outfit_alias: outfit.outfit_alias,
            explanation: 'Synthetic fixture selects only the supplied complete outfit.',
            styling_tips: 'Fixture-only explanation; not fashion advice.',
            item_reasons: outfit.items.map(function (item) {
              return { item_alias: item.item_alias, reason: 'The item is in the backend-provided outfit.' };
            })
          }]
        };
        return sendJson(response, 200, {
          id: 'resp-synthetic-wave2',
          status: 'completed',
          output: [{ type: 'message', content: [{ type: 'output_text', text: JSON.stringify(content) }] }]
        });
      }
      return sendJson(response, 404, {});
    } catch (_) {
      return sendJson(response, 400, {});
    }
  });
}

function geminiHttpFixture() {
  const counts = {
    embeddings: 0,
    tokenCounts: 0,
    generations: 0,
    queryEmbeddings: 0,
    documentEmbeddings: 0
  };
  geminiMockCounts = counts;
  geminiMockGenerationRequests = { countTokens: [], generateContent: [] };
  const server = http.createServer(async function (request, response) {
    try {
      const urlPath = parsePath(request.url);
      const match = urlPath.match(/^\/v1beta\/models\/([^/:]+):(countTokens|batchEmbedContents|generateContent)$/);
      if (request.method !== 'POST' || !match) return sendJson(response, 404, {});
      if (request.headers['x-goog-api-key'] !== geminiFixtureKey) return sendJson(response, 401, {});

      const model = match[1];
      const operation = match[2];
      const body = await readJson(request);
      if (operation === 'countTokens') {
        counts.tokenCounts++;
        if (model === 'gemini-embedding-001') {
          const text = body.contents?.[0]?.parts?.[0]?.text;
          if (typeof text !== 'string' || !text.trim()) return sendJson(response, 400, {});
          return sendJson(response, 200, { totalTokens: 8 });
        }
        if (model !== geminiGenerationModel) return sendJson(response, 404, {});
        const counted = body.generateContentRequest;
        const userText = counted?.contents?.[0]?.parts?.[0]?.text;
        if (!validFlashLiteGenerationConfig(counted?.generationConfig)
            || counted?.model !== 'models/' + geminiGenerationModel
            || typeof userText !== 'string'
            || !counted.systemInstruction?.parts?.[0]?.text
            || !counted.generationConfig.responseFormat.text.schema) {
          return sendJson(response, 400, {});
        }
        geminiMockGenerationRequests.countTokens.push({
          path_model: model,
          request_model: counted.model,
          thinking_level: counted.generationConfig.thinkingConfig.thinkingLevel,
          response_mime_type: counted.generationConfig.responseFormat.text.mimeType,
          structured_schema_present: true,
          legacy_fields_absent: true
        });
        return sendJson(response, 200, {
          totalTokens: userText.includes('á'.repeat(2000)) ? 6001 : 1200
        });
      }

      if (operation === 'batchEmbedContents') {
        if (model !== 'gemini-embedding-001') return sendJson(response, 404, {});
        const inputs = body.requests;
        const input = Array.isArray(inputs) && inputs.length === 1 ? inputs[0] : null;
        const taskType = input?.taskType;
        const text = input?.content?.parts?.[0]?.text;
        if (input?.model !== 'models/gemini-embedding-001'
            || input?.outputDimensionality !== 1536
            || !['RETRIEVAL_QUERY', 'RETRIEVAL_DOCUMENT'].includes(taskType)
            || typeof text !== 'string' || !text.trim()) {
          return sendJson(response, 400, {});
        }
        counts.embeddings++;
        if (taskType === 'RETRIEVAL_QUERY') counts.queryEmbeddings++;
        else counts.documentEmbeddings++;
        return sendJson(response, 200, {
          embeddings: [{ values: Array(1536).fill(0.001) }]
        });
      }

      if (operation === 'generateContent') {
        if (model !== geminiGenerationModel) return sendJson(response, 404, {});
        const config = body.generationConfig;
        const userText = body.contents?.[0]?.parts?.[0]?.text;
        if (body.model || typeof userText !== 'string' || !validFlashLiteGenerationConfig(config)) {
          return sendJson(response, 400, {});
        }
        let input;
        try { input = JSON.parse(userText); } catch (_) { return sendJson(response, 400, {}); }
        const choices = input.complete_outfit_choices;
        if (!Array.isArray(choices) || choices.length < 1) return sendJson(response, 400, {});
        const outfit = choices[0];
        if (!Array.isArray(outfit.items) || outfit.items.length !== 2
            || new Set(outfit.items.map(function (item) { return item.slot; })).size !== 2
            || !outfit.items.some(function (item) { return item.slot === 'TOP'; })
            || !outfit.items.some(function (item) { return item.slot === 'BOTTOM'; })) {
          return sendJson(response, 400, {});
        }
        counts.generations++;
        geminiMockGenerationRequests.generateContent.push({
          path_model: model,
          request_model_in_body: body.model || null,
          thinking_level: config.thinkingConfig.thinkingLevel,
          response_mime_type: config.responseFormat.text.mimeType,
          structured_schema_present: true,
          legacy_fields_absent: true
        });
        if (heldChat) {
          if (chatEnteredResolve) chatEnteredResolve();
          await chatRelease;
          heldChat = false;
        }
        const output = {
          recommendations: [{
            outfit_alias: outfit.outfit_alias,
            explanation: 'Synthetic Gemini HTTP fixture chose one backend-whitelisted outfit.',
            styling_tips: 'Fixture text only; styling quality is not evaluated.',
            item_reasons: outfit.items.map(function (item) {
              return { item_alias: item.item_alias, reason: 'The item belongs to the selected backend candidate.' };
            })
          }]
        };
        return sendJson(response, 200, {
          candidates: [{
            content: { role: 'model', parts: [{ text: JSON.stringify(output) }] },
            finishReason: 'STOP'
          }],
          usageMetadata: { promptTokenCount: 1200, candidatesTokenCount: 120 }
        });
      }

      return sendJson(response, 404, {});
    } catch (_) {
      return sendJson(response, 400, {});
    }
  });
  return { server: server, counts: counts };
}

function validFlashLiteGenerationConfig(config) {
  if (!config || typeof config !== 'object') return false;
  const text = config.responseFormat?.text;
  return Object.keys(config).sort().join(',') === 'candidateCount,maxOutputTokens,responseFormat,thinkingConfig'
    && config.candidateCount === 1
    && config.maxOutputTokens === 2000
    && Object.keys(config.thinkingConfig || {}).sort().join(',') === 'thinkingLevel'
    && config.thinkingConfig.thinkingLevel === 'MINIMAL'
    && Object.keys(config.responseFormat || {}).sort().join(',') === 'text'
    && Object.keys(text || {}).sort().join(',') === 'mimeType,schema'
    && text.mimeType === 'APPLICATION_JSON'
    && !!text.schema && typeof text.schema === 'object'
    && !Object.hasOwn(config, 'thinkingBudget')
    && !Object.hasOwn(config, 'responseMimeType')
    && !Object.hasOwn(config, 'responseSchema');
}

function authFixture() {
  const pair = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
  const publicJwk = pair.publicKey.export({ format: 'jwk' });
  const keyId = crypto.randomBytes(8).toString('hex');
  nextJwks = { keys: [Object.assign(publicJwk, { kid: keyId, use: 'sig', alg: 'RS256' })] };
  function issue(subject, role) {
    const now = Math.floor(Date.now() / 1000);
    const header = Buffer.from(JSON.stringify({ alg: 'RS256', typ: 'JWT', kid: keyId })).toString('base64url');
    const payload = Buffer.from(JSON.stringify({
      iss: 'https://identity.stylemind.local',
      aud: 'stylemind-api',
      sub: subject,
      jti: crypto.randomUUID(),
      iat: now,
      exp: now + 600,
      roles: [role]
    })).toString('base64url');
    const signingInput = header + '.' + payload;
    const signature = crypto.sign('RSA-SHA256', Buffer.from(signingInput), pair.privateKey).toString('base64url');
    const token = signingInput + '.' + signature;
    tokens.push(token);
    return token;
  }
  const server = http.createServer(function (request, response) {
    if (request.method === 'GET' && parsePath(request.url) === '/.well-known/jwks.json') {
      return sendJson(response, 200, nextJwks);
    }
    return sendJson(response, 404, {});
  });
  return { server: server, issue: issue };
}

function writeArtifacts(directory) {
  const policy = {
    review_status: 'APPROVED',
    policy_revision: 'synthetic-wave2-http-v1',
    reviewed_by: 'synthetic-fixture-only',
    category_mappings: [
      { category_id: ids.topCategory, slot: 'TOP', demographic_segment: 'WOMEN', review_status: 'APPROVED', reviewed_by: 'synthetic-fixture-only' },
      { category_id: ids.bottomCategory, slot: 'BOTTOM', demographic_segment: 'WOMEN', review_status: 'APPROVED', reviewed_by: 'synthetic-fixture-only' }
    ],
    colors: [
      { canonical: 'white', aliases: ['trắng'], review_status: 'APPROVED', reviewed_by: 'synthetic-fixture-only' },
      { canonical: 'black', aliases: ['đen'], review_status: 'APPROVED', reviewed_by: 'synthetic-fixture-only' }
    ]
  };
  const artifact = {
    review_status: 'APPROVED',
    artifact_id: 'synthetic-wave2-http-knowledge',
    version: 'fixture-v1',
    reviewed_by: 'synthetic-fixture-only',
    reviewed_at: new Date().toISOString(),
    source_reference: 'synthetic://wave2-http-fixture',
    entries: [{
      review_status: 'APPROVED',
      entry_id: 'fixture-work-linen',
      rule_text: 'Synthetic fixture knowledge: linen is a texture descriptor.',
      reviewed_by: 'synthetic-fixture-only',
      reviewed_at: new Date().toISOString(),
      tags: ['work', 'linen', 'white', 'black', 'minimal']
    }]
  };
  const policyFile = path.join(directory, 'catalog-policy.synthetic.json');
  const knowledgeFile = path.join(directory, 'knowledge-artifact.synthetic.json');
  fs.writeFileSync(policyFile, JSON.stringify(policy), { mode: 0o600 });
  fs.writeFileSync(knowledgeFile, JSON.stringify(artifact), { mode: 0o600 });
  return { policyFile: policyFile, knowledgeFile: knowledgeFile };
}

async function run(command, args, timeout, label, env) {
  const child = spawnLogged(command, args, label, env);
  const result = await waitProcess(child, timeout, label);
  if (result.code !== 0) throw new Error(label + ' failed with exit ' + result.code + ':\n' + redact(outputBuffers.get(child) || ''));
  return outputBuffers.get(child) || '';
}

async function stopChildTree(child) {
  if (!child || child.exitCode !== null) return;
  if (process.platform === 'win32') {
    try { await execLocalFile('taskkill', ['/PID', String(child.pid), '/T', '/F']); } catch (_) { }
  } else {
    try { child.kill('SIGTERM'); } catch (_) { }
  }
  await Promise.race([
    new Promise(function (resolve) { child.once('exit', resolve); }),
    new Promise(function (resolve) { setTimeout(resolve, 5000); })
  ]);
}

async function dockerRun(image, name, containerPort, env) {
  const args = ['run', '--detach', '--rm', '--name', name, '--publish', '127.0.0.1::' + containerPort];
  for (const [key, value] of Object.entries(env || {})) args.push('--env', key + '=' + value);
  args.push(image);
  await run('docker', args, 90000, 'start ' + image);
  const inspection = await execLocalFile('docker', [
    'inspect', '--format',
    '{{(index (index .NetworkSettings.Ports "' + containerPort + '/tcp") 0).HostPort}}',
    name
  ]);
  const port = Number(inspection.stdout.trim());
  if (!Number.isSafeInteger(port) || port < 1) throw new Error('Could not inspect random published port for ' + name);
  return port;
}

async function waitHttp(url, timeoutMs, label, requiredProcess) {
  const until = Date.now() + timeoutMs;
  let lastStatus = 'not responding';
  while (Date.now() < until) {
    if (requiredProcess && requiredProcess.exitCode !== null) {
      throw new Error(label + ' process exited before readiness (' + requiredProcess.exitCode + ')');
    }
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(1200) });
      lastStatus = 'HTTP ' + response.status;
      if (response.ok) return;
    } catch (error) {
      lastStatus = error.name;
    }
    await new Promise(function (resolve) { setTimeout(resolve, 500); });
  }
  throw new Error(label + ' did not become ready (' + lastStatus + ')');
}

async function freePort() {
  const server = net.createServer();
  await new Promise(function (resolve, reject) {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', resolve);
  });
  const port = server.address().port;
  await new Promise(function (resolve) { server.close(resolve); });
  return port;
}

async function awaitChatEntered(timeoutMs) {
  await Promise.race([
    chatEntered,
    new Promise(function (_, reject) { setTimeout(function () { reject(new Error('chat fixture barrier timed out')); }, timeoutMs); })
  ]);
}

async function startGateway(gatewayPort, servicePort) {
  const java = process.env.JAVA_HOME
    ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
    : 'java';
  const child = spawnLogged(java, [...javaTruststoreArgs(),
    '-jar', gatewayJar,
    '--server.port=' + gatewayPort,
    '--stylemind.gateway.stylist-base-url=http://127.0.0.1:' + servicePort
  ], 'gateway');
  await waitHttp('http://127.0.0.1:' + gatewayPort + '/actuator/health', 60000, 'Gateway', child);
  return child;
}

async function gatewayJson(baseUrl, token, method, route, body) {
  const response = await fetch(baseUrl + route, {
    method: method,
    headers: Object.assign({ authorization: 'Bearer ' + token }, body === undefined ? {} : { 'content-type': 'application/json' }),
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(45000)
  });
  const raw = await response.text();
  let json = null;
  try { json = raw ? JSON.parse(raw) : null; } catch (_) { }
  return { status: response.status, headers: response.headers, json: json, text: raw };
}

async function queryContainer(name, sql) {
  const result = await execLocalFile('docker', [
    'exec', name, 'psql', '-U', pgUser, '-d', 'stylemind_stylist_wave2', '-At', '-c', sql
  ]);
  return result.stdout.trim();
}

async function geminiMeterSnapshot() {
  if (!geminiMeterUrl) throw new Error('Gemini live mode requires the local bounded-call meter.');
  const parsed = new URL(geminiMeterUrl);
  if (parsed.protocol !== 'http:' || parsed.hostname !== '127.0.0.1' || parsed.pathname !== '/__summary') {
    throw new Error('Gemini live mode accepts only the local loopback meter summary URL.');
  }
  const response = await fetch(geminiMeterUrl, { signal: AbortSignal.timeout(3000) });
  if (!response.ok) throw new Error('Could not read local Gemini call meter (HTTP ' + response.status + ')');
  return response.json();
}

function assertGeminiBudget(snapshot, additional) {
  const calls = snapshot && snapshot.calls;
  if (!calls || snapshot.stoppedOn429 || calls.blocked !== 0) {
    throw new Error('Gemini meter is not in a safe ready state.');
  }
  if (calls.embedding + additional.embedding > geminiMeterLimits.embedding
      || calls.tokenCount + additional.tokenCount > geminiMeterLimits.tokenCount
      || calls.generation + additional.generation > geminiMeterLimits.generation) {
    throw new Error('Cumulative Gemini smoke budget is insufficient; no provider call was started.');
  }
}

function meterCounts(snapshot) {
  return snapshot && snapshot.calls
    ? { embedding: snapshot.calls.embedding, tokenCount: snapshot.calls.tokenCount,
      generation: snapshot.calls.generation, blocked: snapshot.calls.blocked }
    : null;
}

function providerCallCounts(snapshot) {
  if (snapshot && snapshot.calls) return meterCounts(snapshot);
  if (snapshot && Number.isFinite(snapshot.embeddings)) {
    return {
      embedding: snapshot.embeddings,
      tokenCount: snapshot.tokenCounts,
      generation: snapshot.generations,
      blocked: 0,
      queryEmbeddings: snapshot.queryEmbeddings || 0,
      documentEmbeddings: snapshot.documentEmbeddings || 0
    };
  }
  return null;
}

async function providerSnapshot() {
  if (geminiLiveMode) return geminiMeterSnapshot();
  if (geminiMockMode) return Object.assign({}, geminiMockCounts);
  return Object.assign({}, openAiCounts);
}

function assertCountsUnchanged(before, after, label) {
  if (JSON.stringify(providerCallCounts(before)) !== JSON.stringify(providerCallCounts(after))) {
    throw new Error(label + ' unexpectedly made a Gemini provider call');
  }
}

function assertOperationDelta(before, after, expected, label) {
  const first = providerCallCounts(before);
  const last = providerCallCounts(after);
  for (const operation of ['embedding', 'tokenCount', 'generation']) {
    if (last[operation] - first[operation] !== expected[operation]) {
      throw new Error(label + ' Gemini ' + operation + ' delta was unexpected.');
    }
  }
  if (last.blocked !== first.blocked) throw new Error(label + ' hit the local cumulative call cap.');
}

async function runGeminiPositiveFlow(gatewayBaseUrl, customerAToken, customerBToken, providerStart) {
  const evidence = {
    gateway_http_requests: 0,
    request_status_after_intake: null,
    generation_model: geminiGenerationModel,
    read_back_structural_difference_paths: null,
    read_back_failure_paths: null
  };
  async function call(token, method, route, body) {
    evidence.gateway_http_requests++;
    return gatewayJson(gatewayBaseUrl, token, method, route, body);
  }

  const session = await call(customerAToken, 'POST', '/stylist/api/v1/stylist/sessions', {
    title: 'Synthetic Gemini Flash-Lite verification'
  });
  if (session.status !== 201 || !session.json?.session_id
      || !session.headers.get('cache-control')?.includes('no-store')) {
    throw new Error('Live positive flow could not create the synthetic session through Gateway.');
  }
  evidence.session_status = session.status;

  const intake = await call(customerAToken, 'POST',
    '/stylist/api/v1/stylist/sessions/' + session.json.session_id + '/requests', {
      message: 'Synthetic test only: I need a simple neutral outfit for a workday.',
      occasion: 'work',
      style_context: 'minimal',
      max_budget_vnd: 850000,
      budget_is_hard: true,
      constraints: []
    });
  if (intake.status !== 201 || intake.json?.request_status !== 'RECEIVED'
      || intake.json?.generation_started !== false
      || !intake.headers.get('cache-control')?.includes('no-store')) {
    throw new Error('Live positive flow intake did not remain RECEIVED through Gateway.');
  }
  evidence.request_status_after_intake = intake.json.request_status;
  const beforeGenerate = await providerSnapshot();
  assertCountsUnchanged(providerStart, beforeGenerate, 'Session creation and request intake');

  const generated = await call(customerAToken, 'POST',
    '/stylist/api/v1/stylist/requests/' + intake.json.request_id + '/generate', {});
  if (generated.status !== 200 || generated.json?.request_status !== 'SUCCEEDED'
      || !Array.isArray(generated.json.recommendations) || generated.json.recommendations.length !== 1
      || !generated.headers.get('cache-control')?.includes('no-store')) {
    throw new Error('Live Gemini generation did not return one successful recommendation.');
  }
  const recommendation = generated.json.recommendations[0];
  const slots = recommendation.items?.map(item => item.slot_name).sort();
  const variants = recommendation.items?.map(item => item.shop_variant_id).sort();
  const allowedVariants = [ids.topVariant, ids.bottomVariant].sort();
  if (recommendation.items?.length !== 2 || JSON.stringify(slots) !== JSON.stringify(['BOTTOM', 'TOP'])
      || JSON.stringify(variants) !== JSON.stringify(allowedVariants)
      || recommendation.items.some(item => item.source_type !== 'SHOP_VARIANT' || !item.selection_reason)) {
    throw new Error('Live recommendation violated the synthetic TOP+BOTTOM Commerce whitelist.');
  }
  evidence.generate_status = generated.status;
  evidence.request_status = generated.json.request_status;
  evidence.recommendation_count = generated.json.recommendations.length;
  evidence.item_slots = recommendation.items.map(item => item.slot_name);
  const afterGenerate = await providerSnapshot();
  assertOperationDelta(beforeGenerate, afterGenerate, { embedding: 1, tokenCount: 2, generation: 1 }, 'Gateway generation');

  const list = await call(customerAToken, 'GET',
    '/stylist/api/v1/stylist/requests/' + intake.json.request_id + '/recommendations');
  const readBackComparison = compareGeneratedWithRequestRead(generated.json, list.json, {
    requestId: intake.json.request_id,
    allowedVariantIds: allowedVariants
  });
  evidence.read_back_structural_difference_paths = readBackComparison.structuralDifferencePaths;
  evidence.read_back_failure_paths = readBackComparison.failurePaths;
  if (geminiMockPositiveFlowOnly) {
    const diagnostic = {
      scope: 'SYNTHETIC_GEMINI_HTTP_FIXTURE_ONLY',
      generation_api: 'POST /stylist/api/v1/stylist/requests/{requestId}/generate',
      request_read_api: 'GET /stylist/api/v1/stylist/requests/{requestId}/recommendations',
      generation_http_status: generated.status,
      request_read_http_status: list.status,
      generation_response_contract_failure_paths: readBackComparison.generationFailurePaths,
      request_read_response_contract_failure_paths: readBackComparison.requestReadFailurePaths,
      cross_response_business_field_failure_paths: readBackComparison.crossResponseFailurePaths,
      raw_recommendation_structure_difference_paths: readBackComparison.structuralDifferencePaths,
      cross_response_failure_paths: readBackComparison.failurePaths,
      compared_business_fields: [
        'request_id', 'request_status', 'recommendation_id', 'explanation', 'styling_tips', 'generated_at',
        'item_count', 'source_type', 'shop_variant_id', 'slot_name', 'selection_reason', 'display_order'
      ],
      shop_only_read_contract: 'GET list has wardrobe_item_id=null; generation response has no wardrobe_item_id field',
      credential_or_response_body_values_saved: false
    };
    fs.writeFileSync(path.join(evidenceDir, 'read-back-field-path-diagnostic.json'), JSON.stringify(diagnostic, null, 2));
  }
  if (list.status !== 200 || !list.headers.get('cache-control')?.includes('no-store') || !readBackComparison.valid) {
    throw new Error('Gateway GET-list response violated its API contract or disagreed on shared business fields: '
      + JSON.stringify(readBackComparison.failurePaths));
  }
  const detail = await call(customerAToken, 'GET',
    '/stylist/api/v1/stylist/recommendations/' + recommendation.recommendation_id);
  const detailComparison = compareGeneratedWithDetail(generated.json, detail.json, {
    requestId: intake.json.request_id,
    allowedVariantIds: allowedVariants,
    currentPriceByVariant: { [ids.topVariant]: 250000, [ids.bottomVariant]: 300000 },
    maxBudgetVnd: 850000
  });
  if (detail.status !== 200 || !detail.headers.get('cache-control')?.includes('no-store') || !detailComparison.valid) {
    throw new Error('Gateway detail response violated its contract or current Commerce price/status checks: '
      + JSON.stringify(detailComparison.failurePaths));
  }
  const beforeReadCheck = await providerSnapshot();
  assertCountsUnchanged(afterGenerate, beforeReadCheck, 'Persisted recommendation reads');
  evidence.read_back_status = list.status;
  evidence.detail_status = detail.status;
  evidence.total_current_price_vnd = detail.json.total_current_price_vnd;

  const replay = await call(customerAToken, 'POST',
    '/stylist/api/v1/stylist/requests/' + intake.json.request_id + '/generate', {});
  const replayComparison = compareGenerationReplay(generated.json, replay.json, {
    requestId: intake.json.request_id,
    allowedVariantIds: allowedVariants
  });
  if (replay.status !== 200 || !replay.headers.get('cache-control')?.includes('no-store') || !replayComparison.valid) {
    throw new Error('Gateway terminal replay did not return the original generation response: '
      + JSON.stringify(replayComparison.failurePaths));
  }
  const afterReplay = await providerSnapshot();
  assertCountsUnchanged(beforeReadCheck, afterReplay, 'Terminal replay');
  evidence.replay_status = replay.status;
  evidence.replay_added_provider_calls = 0;

  const foreign = await call(customerBToken, 'POST',
    '/stylist/api/v1/stylist/requests/' + intake.json.request_id + '/generate', {});
  const afterForeign = await providerSnapshot();
  if (foreign.status !== 404 || !foreign.headers.get('cache-control')?.includes('no-store')) {
    throw new Error('Foreign-owner request was not concealed as a no-store 404.');
  }
  assertCountsUnchanged(afterReplay, afterForeign, 'Foreign-owner denial');
  evidence.foreign_owner_status = foreign.status;
  evidence.foreign_owner_added_provider_calls = 0;
  assertOperationDelta(providerStart, afterForeign, { embedding: 1, tokenCount: 2, generation: 1 },
    'Gateway generation, read-back, replay and foreign-owner flow');
  return { evidence, meterAfter: afterForeign, requestId: intake.json.request_id };
}

async function main() {
  function progress(message) { process.stderr.write('[wave2-harness] ' + message + '\n'); }
  fs.mkdirSync(evidenceDir, { recursive: true });
  if (!fs.existsSync(collectionPath) || !fs.existsSync(serviceJar) || !fs.existsSync(gatewayJar)) {
    throw new Error('Build both executable jars and keep the Wave 2 collection in place before running this harness.');
  }

  const commerce = commerceFixture();
  if (geminiLiveMode && (!process.env.STYLEMIND_GEMINI_API_KEY
      || process.env.STYLEMIND_GEMINI_LIVE_CALLS_ENABLED !== 'true' || !geminiMeterUrl)) {
    throw new Error('Gemini live mode requires the process key, explicit live opt-in and local meter.');
  }
  let initialGeminiMeter = null;
  if (geminiLiveMode) {
    const meterPort = Number(process.env.STYLEMIND_GEMINI_METER_PROXY_PORT || 0);
    const meterUrl = new URL(geminiMeterUrl);
    if (!Number.isInteger(meterPort) || meterPort < 1 || meterPort > 65_535 || meterUrl.port !== String(meterPort)) {
      throw new Error('Gemini live mode requires a matching local meter proxy port.');
    }
    initialGeminiMeter = await geminiMeterSnapshot();
    if (geminiMeterProfile === APPROVED_FINAL_GATEWAY_PROFILE) {
      const initial = meterCounts(initialGeminiMeter);
      if (!initial || initial.embedding !== 9 || initial.tokenCount !== 19 || initial.generation !== 5
          || initial.blocked !== 0) {
        throw new Error('Approved final Gateway run requires the verified cumulative baseline 9/19/5 with no blocked calls.');
      }
    }
    assertGeminiBudget(initialGeminiMeter, { embedding: 3, tokenCount: 4, generation: 1 });
  }
  const openAi = geminiMode ? null : openAiFixture();
  const geminiFixture = geminiMockMode ? geminiHttpFixture() : null;
  const auth = authFixture();
  const commercePort = await listen(commerce);
  const openAiPort = openAi ? await listen(openAi) : null;
  const geminiPort = geminiFixture ? await listen(geminiFixture.server) : null;
  const jwksPort = await listen(auth.server);

  let postmanEnvironmentFile = null;
  let exportedEnvironmentFile = null;
  let databaseName = names[0];
  try {
    progress('starting disposable PostgreSQL, Qdrant and Neo4j containers');
    const postgresPort = await dockerRun('postgres:18-alpine', names[0], 5432, {
      POSTGRES_DB: 'stylemind_stylist_wave2',
      POSTGRES_USER: pgUser,
      POSTGRES_PASSWORD: pgPassword
    });
    const qdrantPort = await dockerRun('qdrant/qdrant:v1.20.0', names[1], 6333, {});
    const neo4jPort = await dockerRun('neo4j:5.26-community', names[2], 7687, {
      NEO4J_AUTH: 'neo4j/' + neoPassword
    });
    const qdrantUrl = 'http://127.0.0.1:' + qdrantPort;
    const neo4jUri = 'bolt://127.0.0.1:' + neo4jPort;
    await waitHttp(qdrantUrl + '/collections', 90000, 'Disposable Qdrant');

    const artifacts = writeArtifacts(evidenceDir);
    const servicePort = await freePort();
    progress('starting Stylist with synthetic fixture profiles');
    const java = process.env.JAVA_HOME
      ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
      : 'java';
    const serviceArgs = [...javaTruststoreArgs(),
      '-jar', serviceJar,
      '--server.port=' + servicePort,
      '--spring.profiles.active=catalog-import,knowledge-import' + (geminiMockMode ? ',offline-gemini-mock' : ''),
      ...(geminiLiveMode ? [
        '--stylemind.gemini.meter.proxy-host=127.0.0.1',
        '--stylemind.gemini.meter.proxy-port=' + process.env.STYLEMIND_GEMINI_METER_PROXY_PORT
      ] : []),
      '--spring.datasource.url=jdbc:postgresql://127.0.0.1:' + postgresPort + '/stylemind_stylist_wave2',
      '--spring.datasource.username=' + pgUser,
      '--spring.datasource.password=' + pgPassword,
      '--spring.jpa.hibernate.ddl-auto=validate',
      '--stylemind.security.jwt.jwk-set-uri=http://127.0.0.1:' + jwksPort + '/.well-known/jwks.json',
      '--stylemind.security.jwt.issuer=https://identity.stylemind.local',
      '--stylemind.security.jwt.audience=stylemind-api',
      '--stylemind.clients.commerce.base-url=http://127.0.0.1:' + commercePort,
      '--stylemind.clients.commerce.connect-timeout-ms=1000',
      '--stylemind.clients.commerce.read-timeout-ms=3000',
      '--stylemind.stylist.retrieval.qdrant-url=' + qdrantUrl,
      '--stylemind.stylist.retrieval.collection-alias=' + (geminiMode
        ? 'stylemind_stylist_runtime_gemini_wave2_http'
        : 'stylemind_stylist_runtime_wave2_http'),
      '--stylemind.stylist.retrieval.text-template-revision=wave2-http-synthetic-v1',
      '--stylemind.stylist.catalog.import-enabled=true',
      '--stylemind.stylist.catalog.policy-file=' + artifacts.policyFile,
      '--stylemind.stylist.knowledge.enabled=true',
      '--stylemind.stylist.knowledge.neo4j-uri=' + neo4jUri,
      '--stylemind.stylist.knowledge.neo4j-username=neo4j',
      '--stylemind.stylist.knowledge.neo4j-password=' + neoPassword,
      '--stylemind.stylist.knowledge.artifact-id=synthetic-wave2-http-knowledge',
      '--stylemind.stylist.knowledge.artifact-version=fixture-v1',
      '--stylemind.stylist.knowledge.import-file=' + artifacts.knowledgeFile,
      '--stylemind.stylist.generation.deadline-ms=30000',
      '--management.health.neo4j.enabled=false'
    ];
    if (geminiLiveMode) {
      serviceArgs.push('--stylemind.ai.provider=gemini', '--stylemind.gemini.live-calls-enabled=true',
        '--stylemind.gemini.generation-model=' + geminiGenerationModel);
    } else if (geminiMockMode) {
      serviceArgs.push('--stylemind.ai.provider=gemini',
        '--stylemind.gemini.mock.enabled=true',
        '--stylemind.gemini.mock.base-url=http://127.0.0.1:' + geminiPort,
        '--stylemind.gemini.generation-model=' + geminiGenerationModel,
        '--stylemind.gemini.live-calls-enabled=false');
    } else {
      serviceArgs.push('--stylemind.ai.provider=openai', '--stylemind.openai.enabled=true',
        '--stylemind.openai.api-key=' + fixtureKey,
        '--stylemind.openai.base-url=http://127.0.0.1:' + openAiPort);
    }
    const serviceChild = spawnLogged(java, serviceArgs, 'stylist');
    try {
      await waitHttp('http://127.0.0.1:' + servicePort + '/actuator/health', 120000, 'Stylist', serviceChild);
    } catch (error) {
      if (serviceChild.exitCode !== null) {
        throw new Error('Stylist stopped during startup:\n' + redact(outputBuffers.get(serviceChild) || ''));
      }
      throw error;
    }
    if (serviceChild.exitCode !== null) throw new Error('Stylist stopped during startup:\n' + redact(outputBuffers.get(serviceChild) || ''));

    const gatewayPort = await freePort();
    progress('starting the existing API Gateway route against the disposable Stylist');
    await startGateway(gatewayPort, servicePort);
    if (serviceChild.exitCode !== null) throw new Error('Stylist stopped before Gateway run:\n' + redact(outputBuffers.get(serviceChild) || ''));
    const gatewayBaseUrl = 'http://127.0.0.1:' + gatewayPort;
    const ownerA = crypto.randomUUID();
    const ownerB = crypto.randomUUID();
    const customerAToken = auth.issue(ownerA, 'CUSTOMER');
    const customerBToken = auth.issue(ownerB, 'CUSTOMER');
    let newmanJson = null;
    let newmanStats = null;
    let positiveGatewayFlow = null;
    let afterPostman = null;
    const beforePostmanCalls = await providerSnapshot();
    if (boundedPositiveFlowMode) {
      const catalogStart = geminiLiveMode ? initialGeminiMeter
        : { embeddings: 0, tokenCounts: 0, generations: 0, blocked: 0 };
      assertOperationDelta(catalogStart, beforePostmanCalls,
        { embedding: 2, tokenCount: 2, generation: 0 }, 'Disposable two-candidate catalog import');
      const flow = await runGeminiPositiveFlow(gatewayBaseUrl, customerAToken, customerBToken,
        beforePostmanCalls);
      positiveGatewayFlow = flow.evidence;
      afterPostman = flow.meterAfter;
    } else {
    const collection = JSON.parse(fs.readFileSync(collectionPath, 'utf8'));
    const longMessage = 'á'.repeat(2000);
    const environment = {
      id: crypto.randomUUID(),
      name: 'Wave 2 Disposable Gateway Fixture',
      values: [
        { key: 'gatewayBaseUrl', value: gatewayBaseUrl, enabled: true },
        { key: 'customerAToken', value: customerAToken, enabled: true },
        { key: 'customerBToken', value: customerBToken, enabled: true },
        { key: 'longVietnameseMessage', value: longMessage, enabled: true },
        { key: 'sessionId', value: '', enabled: true },
        { key: 'requestId', value: '', enabled: true },
        { key: 'recommendationId', value: '', enabled: true },
        { key: 'budgetRequestId', value: '', enabled: true }
      ]
    };
    postmanEnvironmentFile = path.join(evidenceDir, 'runtime-environment.json');
    exportedEnvironmentFile = path.join(evidenceDir, 'runtime-environment-after.json');
    fs.writeFileSync(postmanEnvironmentFile, JSON.stringify(environment, null, 2), { mode: 0o600 });

    const npxCli = path.join(path.dirname(process.execPath), 'node_modules', 'npm', 'bin', 'npx-cli.js');
    if (!fs.existsSync(npxCli)) throw new Error('Could not resolve the installed npm npx runtime');
    progress('running the 10-request Newman collection through Gateway');
    const newman = spawnLogged(process.execPath, [
      npxCli,
      '--yes', 'newman@6.2.1', 'run', collectionPath,
      '--environment', postmanEnvironmentFile,
      ...(geminiMode ? ['--export-environment', exportedEnvironmentFile] : []),
      '--reporters', 'cli,junit,json',
      '--reporter-junit-export', path.join(evidenceDir, 'newman-junit.xml'),
      '--reporter-json-export', path.join(evidenceDir, 'newman-report.json')
    ], 'newman');
    const newmanResult = await waitProcess(newman, 180000, 'Newman');
    let newmanOutput = outputBuffers.get(newman) || '';
    writeRedacted(path.join(evidenceDir, 'newman-redacted.log'), newmanOutput);
    if (newmanResult.code !== 0) throw new Error('Newman exited ' + newmanResult.code + ':\n' + redact(newmanOutput));
    if (serviceChild.exitCode !== null) throw new Error('Stylist exited during Newman:\n' + redact(outputBuffers.get(serviceChild) || ''));

    newmanJson = JSON.parse(fs.readFileSync(path.join(evidenceDir, 'newman-report.json'), 'utf8'));
    newmanJson = redactTree(newmanJson);
    fs.writeFileSync(path.join(evidenceDir, 'newman-report.json'), JSON.stringify(newmanJson, null, 2));
    writeRedacted(path.join(evidenceDir, 'newman-junit.xml'),
      fs.readFileSync(path.join(evidenceDir, 'newman-junit.xml'), 'utf8'));
    newmanStats = newmanJson.run.stats;
    if (!newmanStats || newmanStats.requests.failed !== 0 || newmanStats.assertions.failed !== 0) {
      throw new Error('Newman request/assertion summary was not clean');
    }
    afterPostman = await providerSnapshot();
    if (geminiMockMode) {
      const before = providerCallCounts(beforePostmanCalls);
      const after = providerCallCounts(afterPostman);
      if (after.generation - before.generation !== 1
          || after.embedding - before.embedding < 1
          || after.tokenCount - before.tokenCount < 2) {
        throw new Error('Gemini HTTP mock did not observe the expected retrieval embedding, token preflight and one generation.');
      }
    } else if (afterPostman.embeddings !== 3 || afterPostman.tokenCounts !== 2 || afterPostman.generations !== 1) {
      throw new Error('Postman model-call counts were unexpected (catalog import + generation + over-budget preflight only expected): '
        + JSON.stringify(afterPostman));
    }
    }

    let replayEvidence = positiveGatewayFlow ? {
      status: positiveGatewayFlow.replay_status,
      recommendations_match_initial_generate: true,
      provider_calls_added: positiveGatewayFlow.replay_added_provider_calls
    } : null;
    let foreignGenerateStatus = positiveGatewayFlow?.foreign_owner_status ?? null;
    let afterNegativeCalls = positiveGatewayFlow ? providerCallCounts(afterPostman) : null;
    if (geminiMode && !geminiLiveMode && !geminiMockPositiveFlowOnly) {
      const exported = JSON.parse(fs.readFileSync(exportedEnvironmentFile, 'utf8'));
      const envValue = key => exported.values.find(value => value.key === key)?.value;
      const requestId = envValue('requestId');
      if (!requestId) throw new Error('Newman did not export the synthetic request ID.');
      const generatedResponse = newmanResponseJson(newmanJson,
        '3. Generate recommendations (one attempt)');
      if (generatedResponse.request_status !== 'SUCCEEDED'
          || !Array.isArray(generatedResponse.recommendations)) {
        throw new Error('Newman did not capture the initial successful generation response.');
      }
      const route = '/stylist/api/v1/stylist/requests/' + requestId + '/recommendations';
      const read = await gatewayJson(gatewayBaseUrl, customerAToken, 'GET', route);
      if (read.status !== 200 || read.json.request_status !== 'SUCCEEDED'
          || !Array.isArray(read.json.recommendations) || read.json.recommendations.length < 1) {
        throw new Error('Gateway did not persist a successful recommendation for the synthetic request.');
      }
      const beforeReplay = await providerSnapshot();
      const replay = await gatewayJson(gatewayBaseUrl, customerAToken, 'POST',
        '/stylist/api/v1/stylist/requests/' + requestId + '/generate', {});
      const afterReplay = await providerSnapshot();
      assertCountsUnchanged(beforeReplay, afterReplay, 'Terminal replay');
      if (replay.status !== 200 || replay.json.request_status !== 'SUCCEEDED'
          || !isDeepStrictEqual(replay.json.recommendations, generatedResponse.recommendations)) {
        throw new Error('Gateway replay did not return the original successful generation response; mismatch paths: '
          + JSON.stringify(jsonDifferencePaths(generatedResponse.recommendations, replay.json.recommendations)));
      }
      replayEvidence = { status: replay.status, recommendations_match_initial_generate: true, provider_calls_added: 0 };

      const beforeForeign = await providerSnapshot();
      const foreign = await gatewayJson(gatewayBaseUrl, customerBToken, 'POST',
        '/stylist/api/v1/stylist/requests/' + requestId + '/generate', {});
      const afterForeign = await providerSnapshot();
      foreignGenerateStatus = foreign.status;
      if (foreign.status !== 404 || !foreign.headers.get('cache-control')?.includes('no-store')) {
        throw new Error('Foreign-owner generation was not concealed as no-store 404.');
      }
      assertCountsUnchanged(beforeForeign, afterForeign, 'Foreign-owner request');
      afterNegativeCalls = providerCallCounts(afterForeign);
    }

    let loser = null;
    let winner = null;
    let callsAfterConcurrent = null;
    if (!boundedPositiveFlowMode) {
    progress('verifying a concurrent HTTP generate claim against PostgreSQL');
    // HTTP concurrency: one request holds the provider fixture after PostgreSQL claim;
    // a second independent HTTP request to the same request must lose the real DB claim.
    const sessionResult = await gatewayJson(gatewayBaseUrl, customerAToken, 'POST',
      '/stylist/api/v1/stylist/sessions', { title: 'Synthetic concurrency fixture' });
    if (sessionResult.status !== 201) throw new Error('Could not create concurrency session through Gateway');
    const concurrencySession = sessionResult.json.session_id;
    const intake = await gatewayJson(gatewayBaseUrl, customerAToken, 'POST',
      '/stylist/api/v1/stylist/sessions/' + concurrencySession + '/requests', {
        message: 'Synthetic concurrency request for work',
        occasion: 'work',
        style_context: 'minimal',
        max_budget_vnd: 850000,
        budget_is_hard: true,
        constraints: []
      });
    if (intake.status !== 201 || intake.json.request_status !== 'RECEIVED') {
      throw new Error('Could not intake the concurrency request');
    }
    const beforeConcurrentCalls = await providerSnapshot();
    heldChat = true;
    chatEntered = new Promise(function (resolve) { chatEnteredResolve = resolve; });
    chatRelease = new Promise(function (resolve) { chatReleaseResolve = resolve; });
    const firstGeneratePromise = gatewayJson(gatewayBaseUrl, customerAToken, 'POST',
      '/stylist/api/v1/stylist/requests/' + intake.json.request_id + '/generate', {});
    await awaitChatEntered(15000);
    loser = await gatewayJson(gatewayBaseUrl, customerAToken, 'POST',
      '/stylist/api/v1/stylist/requests/' + intake.json.request_id + '/generate', {});
    if (loser.status !== 409 || !loser.headers.get('cache-control').includes('no-store')) {
      chatReleaseResolve();
      throw new Error('Concurrent HTTP loser did not receive the expected no-store 409');
    }
    chatReleaseResolve();
    winner = await firstGeneratePromise;
    if (winner.status !== 200 || winner.json.request_status !== 'SUCCEEDED') {
      throw new Error('Concurrent HTTP winner did not complete successfully');
    }
    const afterConcurrentCalls = await providerSnapshot();
    callsAfterConcurrent = providerCallCounts(afterConcurrentCalls);
    const before = providerCallCounts(beforeConcurrentCalls);
    const after = providerCallCounts(afterConcurrentCalls);
    const delta = {
      embedding: after.embedding - before.embedding,
      tokenCount: after.tokenCount - before.tokenCount,
      generation: after.generation - before.generation
    };
    if (geminiMockMode) {
      if (delta.embedding !== 1 || delta.tokenCount !== 2 || delta.generation !== 1) {
        throw new Error('Concurrent Gemini HTTP mock calls were not limited to the winning request: '
          + JSON.stringify(delta));
      }
    } else if (delta.embedding !== 1 || delta.tokenCount !== 1 || delta.generation !== 1) {
      throw new Error('Concurrent generation did not produce exactly one additional embedding/token-count/chat call');
    }
    }

    const persisted = await queryContainer(names[0], [
      "SELECT count(*) FROM styling_request WHERE request_status = 'SUCCEEDED';",
      'SELECT count(*) FROM wardrobe_recommendation;',
      'SELECT count(*) FROM recommendation_item;',
      "SELECT count(*) FROM stylist_message WHERE speaker_role = 'ASSISTANT';"
    ].join(' '));
    const persistenceRows = persisted.split(/\r?\n/).map(Number);
    if (persistenceRows.length !== 4 || persistenceRows.some(function (value) { return !Number.isFinite(value); })
        || persistenceRows[0] !== (boundedPositiveFlowMode ? 1 : 2)
        || persistenceRows[1] !== (boundedPositiveFlowMode ? 1 : 2)
        || persistenceRows[2] !== (boundedPositiveFlowMode ? 2 : 4) || persistenceRows[3] !== 0) {
      throw new Error('Disposable PostgreSQL persistence aggregate did not match complete generated requests');
    }

    if (geminiMockMode && (
      !geminiMockGenerationRequests
      || geminiMockGenerationRequests.countTokens.length < 1
      || geminiMockGenerationRequests.generateContent.length < 1
      || geminiMockGenerationRequests.countTokens.some(function (request) {
        return request.path_model !== geminiGenerationModel
          || request.request_model !== 'models/' + geminiGenerationModel
          || request.thinking_level !== 'MINIMAL'
          || request.response_mime_type !== 'APPLICATION_JSON'
          || !request.structured_schema_present || !request.legacy_fields_absent;
      })
      || geminiMockGenerationRequests.generateContent.some(function (request) {
        return request.path_model !== geminiGenerationModel
          || request.request_model_in_body !== null
          || request.thinking_level !== 'MINIMAL'
          || request.response_mime_type !== 'APPLICATION_JSON'
          || !request.structured_schema_present || !request.legacy_fields_absent;
      })
    )) {
      throw new Error('The offline Gateway run did not verify the selected Gemini model and request configuration');
    }

    progress('verifying database aggregate and finalizing redacted evidence');
    const summary = {
      status: 'PASS',
      scope: geminiLiveMode
        ? 'Live Gemini through disposable Gateway pipeline; JWT/JWKS and Commerce are synthetic fixtures, not full-system live integration'
        : geminiMockPositiveFlowOnly
          ? 'Gemini HTTP fixture through disposable Gateway positive flow; no provider call, Newman not run'
        : geminiMockMode
          ? 'Gemini Developer API HTTP contract mock through the disposable Gateway pipeline; no provider call'
          : 'OpenAI-compatible fixture flow through disposable Gateway/Newman; no provider call',
      business_flow: 'Customer submits needs and constraints; backend retrieves and hydrates candidates, loads reviewed knowledge, applies P02, sends only the resulting TOP+BOTTOM whitelist to the model adapter, validates the response, rechecks Commerce and persists atomically.',
      gateway_port: gatewayPort,
      stylist_port: servicePort,
      execution: geminiLiveMode ? 'bounded-positive-direct-HTTP-through-Gateway; Newman not run'
        : geminiMockPositiveFlowOnly ? 'bounded-positive-Gemini-HTTP-fixture-through-Gateway; Newman not run'
          : 'checked-in Postman collection executed by Newman through Gateway',
      requests: boundedPositiveFlowMode ? null : newmanStats ? newmanStats.requests.total : null,
      request_failures: boundedPositiveFlowMode ? null : newmanStats ? newmanStats.requests.failed : null,
      assertions: boundedPositiveFlowMode ? null : newmanStats ? newmanStats.assertions.total : null,
      assertion_failures: boundedPositiveFlowMode ? null : newmanStats ? newmanStats.assertions.failed : null,
      postman_model_calls: boundedPositiveFlowMode ? null
        : geminiMockMode ? providerCallCounts(afterPostman) : afterPostman,
      live_gateway_flow: geminiLiveMode ? positiveGatewayFlow : null,
      fixture_gateway_flow: geminiMockPositiveFlowOnly ? positiveGatewayFlow : null,
      gateway_model_calls: geminiLiveMode ? meterCounts(afterPostman) : null,
      initial_gemini_meter_counts: geminiLiveMode ? meterCounts(initialGeminiMeter) : null,
      cumulative_gemini_meter_counts: geminiLiveMode ? meterCounts(await geminiMeterSnapshot()) : null,
      gemini_mock_calls: geminiMockMode ? providerCallCounts(await providerSnapshot()) : null,
      concurrent_http_model_calls_total: callsAfterConcurrent,
      concurrent_loser_status: loser?.status ?? null,
      concurrent_winner_status: winner?.status ?? null,
      replay_evidence: replayEvidence,
      foreign_generate_status: foreignGenerateStatus,
      provider_calls_after_negative_checks: afterNegativeCalls,
      postgres_rows: {
        succeeded_requests: persistenceRows[0],
        recommendations: persistenceRows[1],
        recommendation_items: persistenceRows[2],
        assistant_messages: persistenceRows[3]
      },
      containers: [
        { image: 'postgres:18-alpine', name: names[0], host_port: postgresPort },
        { image: 'qdrant/qdrant:v1.20.0', name: names[1], host_port: qdrantPort },
        { image: 'neo4j:5.26-community', name: names[2], host_port: neo4jPort }
      ],
      fixture_servers: {
        commerce: 'local HTTP Feign contract fixture',
        embeddings_and_responses: geminiLiveMode ? 'live Gemini Developer API; locally metered; synthetic-only payloads'
          : geminiMockMode ? 'local Gemini Developer API HTTP contract fixture; actual Gemini client and Java parser'
            : 'local OpenAI-compatible HTTP contract fixture',
        jwks: 'local RSA JWKS fixture; tokens are not Identity-issued'
      },
      gemini_generation_model: geminiMode ? geminiGenerationModel : null,
      gemini_meter_profile: geminiLiveMode ? (geminiMeterProfile || 'DEFAULT') : null,
      gemini_meter_limits: geminiLiveMode ? geminiMeterLimits : null,
      gemini_generation_model_evidence: geminiMockMode ? {
        model: geminiGenerationModel,
        count_tokens_request_models: [...new Set(geminiMockGenerationRequests.countTokens.map(function (request) {
          return request.path_model + ' | ' + request.request_model;
        }))],
        generate_content_path_models: [...new Set(geminiMockGenerationRequests.generateContent.map(function (request) {
          return request.path_model;
        }))],
        count_tokens_calls_verified: geminiMockGenerationRequests.countTokens.length,
        generate_content_calls_verified: geminiMockGenerationRequests.generateContent.length,
        thinking_levels: [...new Set(geminiMockGenerationRequests.generateContent.map(function (request) {
          return request.thinking_level;
        }))],
        structured_response_mime_types: [...new Set(geminiMockGenerationRequests.generateContent.map(function (request) {
          return request.response_mime_type;
        }))],
        structured_schema_present: geminiMockGenerationRequests.generateContent.every(function (request) {
          return request.structured_schema_present;
        }),
        legacy_fields_absent: geminiMockGenerationRequests.generateContent.every(function (request) {
          return request.legacy_fields_absent;
        })
      } : null,
      catalog_and_knowledge: 'synthetic test-only policy and knowledge artifact'
    };
    fs.writeFileSync(path.join(evidenceDir, 'gateway-newman-summary.json'), JSON.stringify(summary, null, 2));

    // Remove the runtime environment file containing fixture JWTs; reports/logs are redacted.
    if (postmanEnvironmentFile) fs.rmSync(postmanEnvironmentFile, { force: true });
    postmanEnvironmentFile = null;
    if (exportedEnvironmentFile) fs.rmSync(exportedEnvironmentFile, { force: true });
    exportedEnvironmentFile = null;
    process.stdout.write(JSON.stringify(summary, null, 2) + '\n');
  } finally {
    if (chatReleaseResolve) chatReleaseResolve();
    if (postmanEnvironmentFile) fs.rmSync(postmanEnvironmentFile, { force: true });
    if (exportedEnvironmentFile) fs.rmSync(exportedEnvironmentFile, { force: true });
    for (const child of children.slice().reverse()) await stopChildTree(child);
    for (const server of servers.reverse()) {
      try { server.closeAllConnections(); } catch (_) { }
      await new Promise(function (resolve) { try { server.close(resolve); } catch (_) { resolve(); } });
    }
    for (const container of names) {
      try { await execLocalFile('docker', ['rm', '-f', container]); } catch (_) { }
    }
    for (const [child, data] of outputBuffers.entries()) {
      const name = (childLabels.get(child) || 'process').replace(/[^A-Za-z0-9._-]/g, '_');
      const logFile = path.join(evidenceDir, name + '.redacted.log');
      if (data && fs.existsSync(evidenceDir)) {
        try { writeRedacted(logFile, data); } catch (_) { }
      }
    }
  }
}

function redactTree(value) {
  if (typeof value === 'string') return redact(value);
  if (Array.isArray(value)) return value.map(redactTree);
  if (value && typeof value === 'object') {
    for (const key of Object.keys(value)) value[key] = redactTree(value[key]);
  }
  return value;
}

main().catch(function (error) {
  process.stderr.write(redact(error && error.stack ? error.stack : error) + '\n');
  process.exitCode = 1;
});
