#!/usr/bin/env node
'use strict';

const fs = require('node:fs/promises');
const path = require('node:path');
const crypto = require('node:crypto');

const CAP_MICROS = 2_000_000;
const PRICING_VERIFIED_DATE = '2026-11-06';
// Verified from the official model pages on PRICING_VERIFIED_DATE.
const MODEL = 'gpt-5.4-mini-2026-03-17';
const EMBEDDING_MODEL = 'text-embedding-3-small';
const EMBEDDING_INPUT_MICROS_PER_MILLION = 20_000;
const CHAT_INPUT_MICROS_PER_MILLION = 750_000;
const CHAT_OUTPUT_MICROS_PER_MILLION = 4_500_000;
const MAX_INPUT_TOKENS = 6_000;
const MAX_OUTPUT_TOKENS = 2_000;
const OPT_IN = 'YES_I_AUTHORIZE_THE_BOUNDED_SYNTHETIC_SMOKE';
const LOCK_TIMEOUT_MS = 15_000;
const TOKEN_COUNT_PAYLOAD = {
  model: MODEL,
  instructions: 'Choose the supplied synthetic outfit only. Return the required structured result.',
  input: JSON.stringify({
    request: 'Synthetic test only: choose a neutral TOP and BOTTOM for work.',
    complete_outfit_choices: [{ outfit_alias: 'SYNTHETIC_1', items: [
      { item_alias: 'TOP_1', slot: 'TOP', product_name: 'Synthetic blouse', color: 'white' },
      { item_alias: 'BOTTOM_1', slot: 'BOTTOM', product_name: 'Synthetic trousers', color: 'navy' },
    ] }],
    knowledge: ['Synthetic test only: neutral colors can be paired.'],
  }),
  text: { format: { type: 'json_schema', name: 'wave2_synthetic_smoke', strict: true, schema: {
    type: 'object', additionalProperties: false, required: ['outfit_alias'],
    properties: { outfit_alias: { type: 'string', enum: ['SYNTHETIC_1'] } },
  } } },
};

function currentPricingDate() {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Bangkok', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());
}

function microsForTokens(tokens, microsPerMillion) {
  return Math.ceil(tokens * microsPerMillion / 1_000_000);
}

function defaultJournalPath() {
  return path.resolve(__dirname, '..', '.secrets', 'wave2-smoke-reservations.json');
}

async function acquireLock(lockPath, timeoutMs = LOCK_TIMEOUT_MS) {
  const started = Date.now();
  const marker = `${process.pid}:${crypto.randomUUID()}`;
  while (true) {
    try {
      const handle = await fs.open(lockPath, 'wx', 0o600);
      await handle.writeFile(marker, 'utf8');
      await handle.close();
      return async () => {
        try {
          const current = await fs.readFile(lockPath, 'utf8');
          if (current === marker) await fs.unlink(lockPath);
        } catch (error) {
          if (error.code !== 'ENOENT') throw error;
        }
      };
    } catch (error) {
      if (error.code !== 'EEXIST') throw error;
      if (Date.now() - started >= timeoutMs) {
        throw new Error('Reservation lock is busy; refusing to spend without a confirmed lock. Inspect the ignored local lock before retrying.');
      }
      await new Promise(resolve => setTimeout(resolve, 40));
    }
  }
}

async function readJournal(journalPath) {
  try {
    const journal = JSON.parse(await fs.readFile(journalPath, 'utf8'));
    if (journal.version !== 1 || journal.capMicros !== CAP_MICROS || !Array.isArray(journal.reservations)
        || !Number.isSafeInteger(journal.reservedMicros) || journal.reservedMicros < 0) {
      throw new Error('Reservation journal is invalid; refusing to spend. Do not delete or reset it automatically.');
    }
    return journal;
  } catch (error) {
    if (error.code !== 'ENOENT') throw error;
    return { version: 1, capMicros: CAP_MICROS, reservedMicros: 0, reservations: [] };
  }
}

async function atomicWriteJournal(journalPath, journal) {
  const temporary = `${journalPath}.${process.pid}.${crypto.randomUUID()}.tmp`;
  await fs.writeFile(temporary, `${JSON.stringify(journal, null, 2)}\n`, { encoding: 'utf8', mode: 0o600, flag: 'wx' });
  await fs.rename(temporary, journalPath);
}

async function reserve(journalPath, operation, model, amountMicros) {
  if (!Number.isSafeInteger(amountMicros) || amountMicros <= 0) throw new Error('Invalid reservation amount.');
  await fs.mkdir(path.dirname(journalPath), { recursive: true });
  const release = await acquireLock(`${journalPath}.lock`);
  try {
    const journal = await readJournal(journalPath);
    if (journal.reservedMicros + amountMicros > CAP_MICROS) {
      throw new Error('The Wave 2 $2 smoke reservation cap is exhausted; no request was sent.');
    }
    const reservation = {
      id: crypto.randomUUID(),
      operation,
      model,
      reservedMicros: amountMicros,
      reservedAt: new Date().toISOString(),
      delivery: 'UNKNOWN_UNTIL_CALL_RETURNS',
    };
    journal.reservedMicros += amountMicros;
    journal.reservations.push(reservation);
    await atomicWriteJournal(journalPath, journal);
    return reservation;
  } finally {
    await release();
  }
}

async function postJson(fetchImpl, url, apiKey, payload) {
  const response = await fetchImpl(url, {
    method: 'POST',
    redirect: 'error',
    signal: AbortSignal.timeout(8_000),
    headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(payload),
  });
  if (!response.ok) throw new Error(`Provider returned HTTP ${response.status}; reservation remains counted.`);
  return response.json();
}

async function runSmoke({ env = process.env, journalPath = defaultJournalPath(), fetchImpl = fetch } = {}) {
  const apiKey = env.STYLEMIND_OPENAI_API_KEY;
  if (!apiKey) throw new Error('STYLEMIND_OPENAI_API_KEY is not present; no provider call was made.');
  if (env.STYLEMIND_WAVE2_PROVIDER_SMOKE !== OPT_IN) throw new Error('Explicit Wave 2 provider-smoke opt-in is required.');
  if (env.STYLEMIND_WAVE2_PRICING_VERIFIED_AT !== PRICING_VERIFIED_DATE
      || currentPricingDate() !== PRICING_VERIFIED_DATE) {
    throw new Error(`Official prices must be rechecked and the pricing verification date updated before running; verified snapshot is ${PRICING_VERIFIED_DATE}.`);
  }

  const origin = (env.STYLEMIND_OPENAI_BASE_URL || 'https://api.openai.com').replace(/\/$/, '');
  const syntheticEmbedding = 'Synthetic test only: white blouse and navy trousers, work outfit.';
  const embeddingReservationMicros = microsForTokens(MAX_INPUT_TOKENS, EMBEDDING_INPUT_MICROS_PER_MILLION);
  await reserve(journalPath, 'embedding', EMBEDDING_MODEL, embeddingReservationMicros);
  const embedding = await postJson(fetchImpl, `${origin}/v1/embeddings`, apiKey, {
    model: EMBEDDING_MODEL, input: syntheticEmbedding, dimensions: 1_536, encoding_format: 'float',
  });
  const vector = embedding?.data?.[0]?.embedding;
  if (!Array.isArray(vector) || vector.length !== 1_536 || vector.some(value => !Number.isFinite(value))) {
    throw new Error('Embedding fixture/provider response did not match the approved 1536 dimension contract.');
  }

  const countReservationMicros = microsForTokens(MAX_INPUT_TOKENS, CHAT_INPUT_MICROS_PER_MILLION);
  await reserve(journalPath, 'input-token-count-upper-bound', MODEL, countReservationMicros);
  const counted = await postJson(fetchImpl, `${origin}/v1/responses/input_tokens`, apiKey, TOKEN_COUNT_PAYLOAD);
  if (counted.object !== 'response.input_tokens' || !Number.isInteger(counted.input_tokens)
      || counted.input_tokens < 0) throw new Error('Input token count response was invalid; no generation request was sent.');
  if (counted.input_tokens > MAX_INPUT_TOKENS) throw new Error('Synthetic generation input exceeds 6000 tokens; no generation request was sent.');

  const generationReservationMicros = microsForTokens(MAX_INPUT_TOKENS, CHAT_INPUT_MICROS_PER_MILLION)
      + microsForTokens(MAX_OUTPUT_TOKENS, CHAT_OUTPUT_MICROS_PER_MILLION);
  await reserve(journalPath, 'structured-generation-upper-bound', MODEL, generationReservationMicros);
  const response = await postJson(fetchImpl, `${origin}/v1/responses`, apiKey, {
    ...TOKEN_COUNT_PAYLOAD,
    store: false,
    max_output_tokens: MAX_OUTPUT_TOKENS,
  });
  if (response.status !== 'completed') throw new Error('Generation did not complete; its reservation remains counted.');
  const usage = response.usage || {};
  return {
    status: 'completed',
    model: MODEL,
    embeddingModel: EMBEDDING_MODEL,
    dimensions: vector.length,
    inputTokens: usage.input_tokens ?? counted.input_tokens,
    outputTokens: usage.output_tokens ?? null,
    reservedMicros: embeddingReservationMicros + countReservationMicros + generationReservationMicros,
  };
}

if (require.main === module) {
  runSmoke().then(result => {
    process.stdout.write(`${JSON.stringify(result)}\n`);
  }).catch(error => {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  });
}

module.exports = {
  CAP_MICROS,
  MAX_INPUT_TOKENS,
  MAX_OUTPUT_TOKENS,
  MODEL,
  EMBEDDING_MODEL,
  PRICING_VERIFIED_DATE,
  OPT_IN,
  defaultJournalPath,
  reserve,
  runSmoke,
};
