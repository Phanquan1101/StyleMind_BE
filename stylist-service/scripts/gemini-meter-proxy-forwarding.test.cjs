'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const { buildUpstreamForward } = require('./gemini-meter-proxy-forwarding.cjs');

test('Gemini meter proxy forwards the captured JSON Buffer unchanged', () => {
  const body = Buffer.from(JSON.stringify({
    content: { parts: [{ text: 'synthetic contract payload' }] },
    embedContentConfig: { taskType: 'RETRIEVAL_QUERY', outputDimensionality: 1536 }
  }), 'utf8');
  const request = {
    method: 'POST',
    headers: {
      host: 'generativelanguage.googleapis.com',
      connection: 'keep-alive',
      'content-length': String(body.length),
      'content-type': 'application/json',
      'x-goog-api-key': 'synthetic-fixture-only'
    }
  };
  const signal = AbortSignal.timeout(1000);
  const forwarded = buildUpstreamForward(
    request,
    new URL('/v1beta/models/gemini-embedding-001:embedContent?alt=json', 'https://generativelanguage.googleapis.com'),
    'generativelanguage.googleapis.com',
    body,
    signal
  );

  assert.equal(forwarded.url,
    'https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent?alt=json');
  assert.equal(forwarded.options.body, body);
  assert.deepEqual(Buffer.from(forwarded.options.body), body);
  assert.equal(forwarded.options.headers.get('content-type'), 'application/json');
  assert.equal(forwarded.options.headers.get('x-goog-api-key'), 'synthetic-fixture-only');
  assert.equal(forwarded.options.headers.has('host'), false);
  assert.equal(forwarded.options.headers.has('content-length'), false);
  assert.equal(forwarded.options.signal, signal);
});
