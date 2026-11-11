#!/usr/bin/env node
'use strict';
/*
 * Simulates a SePay "money received" webhook against the local stack. LOCAL USE ONLY.
 *
 *   node scripts/simulate-sepay-webhook.cjs --reference SMEDA9EC9A7C56 --amount 746000
 *
 * Options
 *   --reference <code>   payment reference shown on the QR page / returned as "reference" by payment-attempt (required)
 *   --amount <vnd>       transferred amount in VND; must equal the attempt amount to settle (required)
 *   --duplicate          send the same event twice (second delivery must be treated as a duplicate)
 *   --late               use a transaction date after the attempt expired (recorded as LATE_PAYMENT)
 *   --date "yyyy-MM-dd HH:mm:ss"  transaction date in Vietnam time (default: now). Use the real transfer time to replay a
 *                        real payment whose webhook never arrived (it is compared with the attempt expiry, not with today)
 *   --event-id <n>       provider event id (default: current time in ms; reuse to test idempotency)
 *   --base-url <url>     gateway base URL (default http://localhost:8080); use your ngrok URL to send through the tunnel,
 *                        e.g. --base-url https://<your-domain>.ngrok-free.dev
 *   --url <url>          full webhook URL (overrides --base-url); default <base-url>/payment/internal/v1/sepay/webhooks
 *   --checkout <id>      optional QR checkout id: poll its status afterwards (needs --email)
 *   --email <email>      account that owns the checkout; password from SEED_PASSWORD (default Stylemind@123)
 *
 * The signing secret is read at run time from SEPAY_WEBHOOK_SECRET or from ../.env
 * (STYLEMIND_SEPAY_WEBHOOK_SECRET). It is never printed or stored.
 *
 * Signature (payment-service SepayWebhookAuthenticationServiceImpl, mode "hmac"):
 *   X-SePay-Timestamp: epoch seconds (+-5 min)
 *   X-SePay-Signature: "sha256=" + hex(HMAC_SHA256(secret, timestamp + "." + rawBody))
 * The webhook answers 200 {"success":true} even when the event is recorded as an exception
 * (wrong amount, late, duplicate...), so the real outcome is visible on the QR page / order, or via --checkout.
 */
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

function parseArgs(argv) {
  const out = {};
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (!a.startsWith('--')) continue;
    const key = a.slice(2);
    const next = argv[i + 1];
    if (next === undefined || next.startsWith('--')) out[key] = true; else { out[key] = next; i++; }
  }
  return out;
}
const args = parseArgs(process.argv.slice(2));

function fail(msg) { console.error('ERROR: ' + msg); process.exit(1); }

if (args.help || !args.reference || !args.amount) {
  console.log(fs.readFileSync(__filename, 'utf8').split('*/')[0].replace(/^#!.*\n'use strict';\n\/\*\n?/, ''));
  process.exit(args.help ? 0 : 1);
}
const amount = Number(args.amount);
if (!Number.isInteger(amount) || amount <= 0) fail('--amount must be a positive integer (VND)');
const reference = String(args.reference).trim();
if (!/^[A-Za-z0-9_-]{4,64}$/.test(reference)) fail('--reference looks invalid');

function readSecret() {
  if (process.env.SEPAY_WEBHOOK_SECRET) return process.env.SEPAY_WEBHOOK_SECRET;
  try {
    const env = fs.readFileSync(path.join(__dirname, '..', '.env'), 'utf8');
    const m = env.match(/^STYLEMIND_SEPAY_WEBHOOK_SECRET=(.*)$/m);
    if (m && m[1].trim()) return m[1].trim();
  } catch { /* fall through */ }
  return null;
}
const secret = readSecret();
if (!secret) fail('webhook secret not found (set SEPAY_WEBHOOK_SECRET or STYLEMIND_SEPAY_WEBHOOK_SECRET in .env)');

const baseUrl = String(args['base-url'] || 'http://localhost:8080').replace(/\/+$/, '');
if (!/^https?:\/\//.test(baseUrl)) fail('--base-url must start with http:// or https://');
const url = args.url || baseUrl + '/payment/internal/v1/sepay/webhooks';
const eventId = args['event-id'] ? Number(args['event-id']) : Date.now();
if (!Number.isInteger(eventId) || eventId <= 0) fail('--event-id must be a positive integer');

// SePay sends transactionDate as "yyyy-MM-dd HH:mm:ss" in Vietnam time (UTC+7).
function vietnamTime(offsetMs) {
  return new Date(Date.now() + 7 * 3600 * 1000 + offsetMs).toISOString().slice(0, 19).replace('T', ' ');
}
if (args.date && !/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(String(args.date))) fail('--date must look like "2026-11-12 02:05:30"');
const transactionDate = args.date ? String(args.date) : vietnamTime(args.late ? 24 * 3600 * 1000 : -1000);

const body = JSON.stringify({
  id: eventId,
  gateway: 'MBBank',
  transactionDate,
  accountNumber: '0000000000',
  subAccount: null,
  code: reference,
  content: `${reference} thanh toan don hang StyleMind`,
  transferType: 'in',
  description: `BankAPINotify ${reference}`,
  transferAmount: amount,
  accumulated: amount,
  referenceCode: 'FT' + eventId,
});

async function send(label) {
  const timestamp = String(Math.floor(Date.now() / 1000));
  const signature = 'sha256=' + crypto.createHmac('sha256', secret).update(`${timestamp}.${body}`).digest('hex');
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'content-type': 'application/json', 'X-SePay-Timestamp': timestamp, 'X-SePay-Signature': signature, 'ngrok-skip-browser-warning': '1' },
    body,
  });
  const text = await res.text();
  console.log(`${label}: HTTP ${res.status} ${text.slice(0, 200)}`);
  if (res.status === 401) console.log('  -> signature rejected: wrong secret, or the payment-service secret differs from .env');
  return res.status;
}

async function pollCheckout() {
  if (!args.checkout) return;
  if (!args.email) { console.log('--checkout needs --email to log in; skipping status check'); return; }
  const base = args.url ? new URL(args.url).origin : baseUrl;
  const login = await fetch(base + '/api/v1/auth/login', {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ email: args.email, password: process.env.SEED_PASSWORD || 'Stylemind@123' }),
  });
  if (!login.ok) { console.log('login for status check failed: HTTP ' + login.status); return; }
  const token = (await login.json()).access_token;
  for (let i = 0; i < 12; i++) {
    const r = await fetch(`${base}/commerce/api/v1/qr-checkouts/${args.checkout}`, { headers: { authorization: 'Bearer ' + token } });
    const j = await r.json().catch(() => ({}));
    console.log(`checkout status [${i + 1}/12]: ${j.status || 'HTTP ' + r.status}${j.orderId ? ' orderId=' + j.orderId : ''}`);
    if (j.status === 'ORDER_CREATED' || j.status === 'EXPIRED' || j.status === 'CANCELLED') return;
    await new Promise((s) => setTimeout(s, 5000)); // payment -> commerce outbox runs about every 10 s
  }
}

(async () => {
  console.log(`event id ${eventId}, reference ${reference}, amount ${amount}, transactionDate ${transactionDate}${args.late ? ' (LATE)' : ''}${args.date ? ' (custom date)' : ''}`);
  const status = await send('webhook');
  if (args.duplicate) await send('webhook (duplicate, same event id)');
  if (status === 200) await pollCheckout();
})().catch((e) => fail(e.message));
