'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const smoke = require('./wave2-provider-smoke.cjs');

async function fixtureDirectory() {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'stylemind-wave2-smoke-'));
  return { directory, journal: path.join(directory, 'reservations.json') };
}

function environment(journal, extra = {}) {
  return {
    STYLEMIND_OPENAI_API_KEY: 'fixture-secret-never-written',
    STYLEMIND_WAVE2_PROVIDER_SMOKE: smoke.OPT_IN,
    STYLEMIND_WAVE2_PRICING_VERIFIED_AT: smoke.PRICING_VERIFIED_DATE,
    journalPath: journal,
    ...extra,
  };
}

async function readJournal(journalPath) {
  return JSON.parse(await fs.readFile(journalPath, 'utf8'));
}

function okJson(body) {
  return { ok: true, status: 200, json: async () => body };
}

test('missing key refuses without network or journal', async t => {
  const { directory, journal } = await fixtureDirectory();
  t.after(() => fs.rm(directory, { recursive: true, force: true }));
  let calls = 0;
  await assert.rejects(smoke.runSmoke({ env: environment(journal, { STYLEMIND_OPENAI_API_KEY: '' }),
    journalPath: journal, fetchImpl: async () => { calls++; } }), /not present/);
  assert.equal(calls, 0);
  await assert.rejects(fs.stat(journal), { code: 'ENOENT' });
});

test('exhausted budget sends no request', async t => {
  const { directory, journal } = await fixtureDirectory();
  t.after(() => fs.rm(directory, { recursive: true, force: true }));
  await fs.mkdir(path.dirname(journal), { recursive: true });
  await fs.writeFile(journal, JSON.stringify({ version: 1, capMicros: smoke.CAP_MICROS,
    reservedMicros: smoke.CAP_MICROS - 1, reservations: [] }));
  let calls = 0;
  await assert.rejects(smoke.runSmoke({ env: environment(journal), journalPath: journal,
    fetchImpl: async () => { calls++; return okJson({}); } }), /cap is exhausted/);
  assert.equal(calls, 0);
});

test('competing reservation processes cannot reserve beyond the cap', async t => {
  const { directory, journal } = await fixtureDirectory();
  t.after(() => fs.rm(directory, { recursive: true, force: true }));
  const attempts = await Promise.allSettled([
    smoke.reserve(journal, 'test-a', smoke.MODEL, 1_500_000),
    smoke.reserve(journal, 'test-b', smoke.MODEL, 1_500_000),
  ]);
  assert.equal(attempts.filter(result => result.status === 'fulfilled').length, 1);
  assert.equal(attempts.filter(result => result.status === 'rejected').length, 1);
  const journalState = await readJournal(journal);
  assert.equal(journalState.reservedMicros, 1_500_000);
  assert.equal(journalState.reservations.length, 1);
});

test('a later invocation continues the restart-safe journal without resetting reservations', async t => {
  const { directory, journal } = await fixtureDirectory();
  t.after(() => fs.rm(directory, { recursive: true, force: true }));
  await smoke.reserve(journal, 'first-process', smoke.MODEL, 250_000);
  await smoke.reserve(journal, 'second-process', smoke.MODEL, 500_000);
  const journalState = await readJournal(journal);
  assert.equal(journalState.reservedMicros, 750_000);
  assert.deepEqual(journalState.reservations.map(row => row.operation), ['first-process', 'second-process']);
});

test('provider timeout leaves every pre-call reservation counted and stores no secrets or prompt', async t => {
  const { directory, journal } = await fixtureDirectory();
  t.after(() => fs.rm(directory, { recursive: true, force: true }));
  let calls = 0;
  const fetchImpl = async (url, options) => {
    calls++;
    assert.equal(options.redirect, 'error');
    assert.match(options.headers.Authorization, /^Bearer /);
    if (calls === 1) return okJson({ data: [{ embedding: Array(1536).fill(0.01) }] });
    if (calls === 2) return okJson({ object: 'response.input_tokens', input_tokens: 50 });
    throw Object.assign(new Error('timed out'), { name: 'AbortError' });
  };
  await assert.rejects(smoke.runSmoke({ env: environment(journal), journalPath: journal, fetchImpl }), /timed out/);
  const journalState = await readJournal(journal);
  assert.equal(calls, 3);
  assert.equal(journalState.reservedMicros, 18_120);
  assert.equal(journalState.reservations.length, 3);
  const serialized = JSON.stringify(journalState);
  assert.doesNotMatch(serialized, /fixture-secret|Synthetic test only|Authorization|input_tokens/);
});

test('above-cap current request is rejected before it is sent', async t => {
  const { directory, journal } = await fixtureDirectory();
  t.after(() => fs.rm(directory, { recursive: true, force: true }));
  await fs.mkdir(path.dirname(journal), { recursive: true });
  await fs.writeFile(journal, JSON.stringify({ version: 1, capMicros: smoke.CAP_MICROS,
    reservedMicros: smoke.CAP_MICROS - 100, reservations: [] }));
  let calls = 0;
  await assert.rejects(smoke.runSmoke({ env: environment(journal), journalPath: journal,
    fetchImpl: async () => { calls++; return okJson({}); } }), /cap is exhausted/);
  assert.equal(calls, 0);
});

test('exact token count over budget suppresses the chat request without releasing reservations', async t => {
  const { directory, journal } = await fixtureDirectory();
  t.after(() => fs.rm(directory, { recursive: true, force: true }));
  let calls = 0;
  const fetchImpl = async () => {
    calls++;
    if (calls === 1) return okJson({ data: [{ embedding: Array(1536).fill(0.01) }] });
    return okJson({ object: 'response.input_tokens', input_tokens: 6_001 });
  };
  await assert.rejects(smoke.runSmoke({ env: environment(journal), journalPath: journal, fetchImpl }), /exceeds 6000/);
  const journalState = await readJournal(journal);
  assert.equal(calls, 2);
  assert.equal(journalState.reservations.length, 2);
  assert.equal(journalState.reservedMicros, 4_620);
});
