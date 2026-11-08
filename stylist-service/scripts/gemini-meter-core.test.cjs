'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const {
  DEFAULT_LIMITS, APPROVED_FINAL_GATEWAY_PROFILE, APPROVED_FINAL_GATEWAY_LIMITS,
  limitsForProfile, classifyOperation, reserveAttempt, markDispatched,
  completeAttempt, failAttempt, safeProviderErrorDiagnostic, readBoundedResponse,
  safeTokenCountRequestMetadata
} = require('./gemini-meter-core.cjs');

test('all Gemini Developer API smoke routes classify into the shared meter', () => {
  assert.deepEqual(classifyOperation('POST', '/v1beta/models/gemini-2.5-flash:countTokens'), {
    operation: 'tokenCount', model: 'gemini-2.5-flash'
  });
  assert.deepEqual(classifyOperation('POST', '/v1beta/models/gemini-2.5-flash:generateContent'), {
    operation: 'generation', model: 'gemini-2.5-flash'
  });
  assert.deepEqual(classifyOperation('POST', '/v1beta/models/gemini-embedding-001:batchEmbedContents'), {
    operation: 'embedding', model: 'gemini-embedding-001', endpointAction: 'batchEmbedContents'
  });
  assert.deepEqual(classifyOperation('POST', '/v1beta/models/gemini-embedding-001:embedContent'), {
    operation: 'embedding', model: 'gemini-embedding-001', endpointAction: 'embedContent'
  });
  assert.deepEqual(classifyOperation('GET', '/v1beta/models'), { operation: 'metadata', model: null });
  assert.equal(classifyOperation('POST', '/v1beta/models/gemini-2.5-flash:unknown'), null);
  assert.equal(DEFAULT_LIMITS.tokenCount, 20);
  assert.deepEqual(APPROVED_FINAL_GATEWAY_LIMITS, { embedding: 12, tokenCount: 23, generation: 6 });
  assert.equal(limitsForProfile(''), DEFAULT_LIMITS);
  assert.equal(limitsForProfile(APPROVED_FINAL_GATEWAY_PROFILE), APPROVED_FINAL_GATEWAY_LIMITS);
  assert.throws(() => limitsForProfile('UNAPPROVED'), /meter-profile-unsupported/);
});

test('provider attempt is reserved before dispatch and HTTP 400 remains in cumulative usage', () => {
  const summary = { calls: { tokenCount: 7 }, events: [] };
  const event = reserveAttempt(summary, 'tokenCount', 'gemini-2.5-flash');
  assert.equal(summary.calls.tokenCount, 8);
  assert.equal(event.dispatched, false);
  markDispatched(event);
  completeAttempt(event, 400, 42);
  assert.equal(summary.calls.tokenCount, 8);
  assert.deepEqual(event, {
    operation: 'tokenCount', model: 'gemini-2.5-flash', status: 400,
    reserved: true, dispatched: true, latencyMs: 42
  });
});

test('timeout and connection failures retain reserved attempts', () => {
  for (const errorName of ['TimeoutError', 'TypeError']) {
    const summary = { calls: { tokenCount: 0 }, events: [] };
    const event = reserveAttempt(summary, 'tokenCount', 'gemini-2.5-flash');
    markDispatched(event);
    failAttempt(event, Object.assign(new Error('private diagnostic'), { name: errorName }), 15_000);
    assert.equal(summary.calls.tokenCount, 1);
    assert.equal(event.status, 502);
    assert.equal(event.error, errorName);
    assert.equal(JSON.stringify(event).includes('private diagnostic'), false);
  }
});

test('safe upstream diagnostics retain only status, code and field paths', () => {
  const fixture = Buffer.from(JSON.stringify({
    error: {
      code: 400,
      status: 'INVALID_ARGUMENT',
      message: 'prompt=synthetic private prompt; x-goog-api-key=synthetic-secret',
      details: [{ fieldViolations: [{ field: 'generateContentRequest.generationConfig.responseFormat' }],
        description: 'synthetic secret and prompt must not persist' }]
    }
  }));
  const safe = safeProviderErrorDiagnostic(400, fixture);
  assert.deepEqual(safe, {
    httpStatus: 400,
    code: 400,
    status: 'INVALID_ARGUMENT',
    fieldPaths: ['generateContentRequest.generationConfig.responseFormat']
  });
  assert.doesNotMatch(JSON.stringify(safe), /synthetic private prompt|synthetic-secret|description|message/);
});

test('countTokens request metadata keeps system/input field paths and never stores prompt text', () => {
  const body = Buffer.from(JSON.stringify({
    generateContentRequest: {
      model: 'models/gemini-2.5-flash',
      systemInstruction: { parts: [{ text: 'synthetic private system prompt' }] },
      contents: [{ role: 'user', parts: [{ text: 'synthetic private user prompt' }] }],
      generationConfig: {
        responseMimeType: 'application/json',
        responseSchema: { type: 'OBJECT' },
        thinkingConfig: { thinkingBudget: 0 }
      }
    }
  }));
  const safe = safeTokenCountRequestMetadata({ model: 'gemini-2.5-flash' }, body);
  assert.deepEqual(safe, {
    valid: true,
    requestKind: 'generateContentRequest',
    model: 'models/gemini-2.5-flash',
    requestFields: [
      'generateContentRequest.model',
      'generateContentRequest.systemInstruction',
      'generateContentRequest.contents'
    ],
    generationConfigFields: [
      'generateContentRequest.generationConfig.responseMimeType',
      'generateContentRequest.generationConfig.responseSchema',
      'generateContentRequest.generationConfig.thinkingConfig'
    ]
  });
  assert.doesNotMatch(JSON.stringify(safe), /private system prompt|private user prompt/);
});

test('diagnostic parser ignores over-limit response body and bounded reader truncates without retaining tail', async () => {
  const oversized = Buffer.alloc(64 * 1024 + 1, 0x61);
  assert.deepEqual(safeProviderErrorDiagnostic(400, oversized, true), { httpStatus: 400, bodyTruncated: true });

  const response = new Response(new Uint8Array([1, 2, 3, 4, 5]));
  const bounded = await readBoundedResponse(response, 3);
  assert.equal(bounded.truncated, true);
  assert.deepEqual([...bounded.bytes], [1, 2, 3]);
});

test('meter refuses an over-cap attempt without reserving or dispatching it', () => {
  const summary = { calls: { tokenCount: 20 }, events: [] };
  assert.equal(reserveAttempt(summary, 'tokenCount', 'gemini-2.5-flash'), null);
  assert.equal(summary.calls.tokenCount, 20);
  assert.equal(summary.events.length, 0);
});

test('approved final Gateway profile admits only the exact cumulative remainder', () => {
  const summary = { calls: { embedding: 9, tokenCount: 19, generation: 5 }, events: [] };
  for (let index = 0; index < 3; index++) {
    assert.ok(reserveAttempt(summary, 'embedding', 'gemini-embedding-001', APPROVED_FINAL_GATEWAY_LIMITS));
  }
  for (let index = 0; index < 4; index++) {
    assert.ok(reserveAttempt(summary, 'tokenCount', 'gemini-3.5-flash-lite', APPROVED_FINAL_GATEWAY_LIMITS));
  }
  assert.ok(reserveAttempt(summary, 'generation', 'gemini-3.5-flash-lite', APPROVED_FINAL_GATEWAY_LIMITS));
  assert.deepEqual(summary.calls, { embedding: 12, tokenCount: 23, generation: 6 });
  assert.equal(reserveAttempt(summary, 'embedding', 'gemini-embedding-001', APPROVED_FINAL_GATEWAY_LIMITS), null);
  assert.equal(reserveAttempt(summary, 'tokenCount', 'gemini-3.5-flash-lite', APPROVED_FINAL_GATEWAY_LIMITS), null);
  assert.equal(reserveAttempt(summary, 'generation', 'gemini-3.5-flash-lite', APPROVED_FINAL_GATEWAY_LIMITS), null);
  assert.deepEqual(summary.calls, { embedding: 12, tokenCount: 23, generation: 6 });
});
