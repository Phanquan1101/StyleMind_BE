#!/usr/bin/env node
'use strict';
/*
 * Seeds local demo accounts and outfits through the public API (Gateway) only. LOCAL USE ONLY.
 *
 *   node scripts/seed-test-data.cjs
 *
 * Needs: the stack running and e-mail delivered to Mailpit
 *   docker compose -f docker-compose.yml -f docker-compose.mailpit.yml up -d
 *
 * Idempotent: re-running logs in to existing accounts and skips what already exists.
 * Environment (all optional):
 *   GATEWAY_URL   default http://localhost:8080
 *   MAILPIT_URL   default http://localhost:8025
 *   FRONTEND_URL  default http://localhost:3000 (only used to print the public link)
 *   SEED_PASSWORD default Stylemind@123 (must be 12+ characters)
 *
 * The first admin comes from the Compose bootstrap (.env). If .env has the bootstrap admin credentials,
 * this script reads them at run time (never printed) to promote admin@sm.test to ADMIN.
 */
const fs = require('fs');
const path = require('path');

const GATEWAY = (process.env.GATEWAY_URL || 'http://localhost:8080').replace(/\/+$/, '');
const MAILPIT = (process.env.MAILPIT_URL || 'http://localhost:8025').replace(/\/+$/, '');
const FRONTEND = (process.env.FRONTEND_URL || 'http://localhost:3000').replace(/\/+$/, '');
const PASSWORD = process.env.SEED_PASSWORD || 'Stylemind@123';

const ACCOUNTS = {
  an: { email: 'an@sm.test', name: 'An', phone: '0912345678', height: 165, weight: 52 },
  binh: { email: 'binh@sm.test', name: 'Bình', phone: '0923456789', height: 175, weight: 68 },
  chi: { email: 'chi@sm.test', name: 'Chi', phone: '0934567890', height: 158, weight: 48 },
};
const ADMIN_EMAIL = 'admin@sm.test';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const log = (msg) => console.log(msg);

async function api(method, route, { body, token, query } = {}) {
  const url = GATEWAY + route + (query ? '?' + new URLSearchParams(query) : '');
  const res = await fetch(url, {
    method,
    headers: { ...(body ? { 'content-type': 'application/json' } : {}), ...(token ? { authorization: 'Bearer ' + token } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  let json = null;
  const text = await res.text();
  try { json = text ? JSON.parse(text) : null; } catch { json = { raw: text.slice(0, 200) }; }
  return { status: res.status, json };
}
function must(res, what) {
  if (res.status >= 400) throw new Error(`${what} failed: HTTP ${res.status} ${JSON.stringify(res.json).slice(0, 300)}`);
  return res.json;
}
const subjectOf = (token) => JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8')).sub;

async function otpFromMailpit(email, since) {
  for (let i = 0; i < 40; i++) {
    let messages = [];
    try { messages = (await (await fetch(MAILPIT + '/api/v1/messages?limit=50')).json()).messages || []; }
    catch { throw new Error('Mailpit is not reachable at ' + MAILPIT + ' (start the stack with docker-compose.mailpit.yml)'); }
    const hit = messages.find((m) => (m.To || []).some((t) => t.Address.toLowerCase() === email) && new Date(m.Created).getTime() >= since);
    if (hit) {
      const full = await (await fetch(MAILPIT + '/api/v1/message/' + hit.ID)).json();
      const code = ((full.Text || '') + ' ' + (full.HTML || '')).match(/\b(\d{6})\b/);
      if (code) return code[1];
    }
    await sleep(1500);
  }
  throw new Error('No verification e-mail for ' + email + ' in Mailpit. Is SMTP routed to Mailpit?');
}

async function ensureAccount(email) {
  let login = await api('POST', '/api/v1/auth/login', { body: { email, password: PASSWORD } });
  if (login.status === 200) return { token: login.json.access_token, created: false };
  let since = Date.now() - 2000;
  const reg = await api('POST', '/api/v1/auth/register', { body: { email, password: PASSWORD } });
  // 409 = exists; 5xx on a cold stack = account created but the verification e-mail call timed out. Both mean "pending verification".
  if (reg.status >= 400 && reg.status !== 409 && reg.status < 500) must(reg, 'register ' + email);
  if (reg.status === 409 || reg.status >= 500 || login.status === 403) {
    since = Date.now() - 10 * 60 * 1000; // an earlier e-mail may already be in Mailpit
    let sent = false;
    for (let attempt = 0; attempt < 6 && !sent; attempt++) {
      const resend = await api('POST', '/api/v1/auth/resend-email-verification', { body: { email } });
      if (resend.status < 400) { sent = true; since = Date.now() - 2000; break; }
      if (resend.status === 429) { log(`  ${email}: resend is rate-limited, waiting 20s...`); await sleep(20000); continue; }
      throw new Error(`${email} already exists and cannot be logged in with SEED_PASSWORD (HTTP ${login.status}); resend failed (${resend.status}). Reset the database volume or use the right password.`);
    }
  }
  const code = await otpFromMailpit(email, since);
  must(await api('POST', '/api/v1/auth/verify-email', { body: { email, code } }), 'verify-email ' + email);
  login = await api('POST', '/api/v1/auth/login', { body: { email, password: PASSWORD } });
  return { token: must(login, 'login ' + email).access_token, created: true };
}

async function ensureProfile(token, acct) {
  const me = must(await api('GET', '/api/v1/me', { token }), 'get profile');
  if (!me.profile || me.profile.display_name !== acct.name) must(await api('PATCH', '/api/v1/me', { token, body: { display_name: acct.name } }), 'patch profile');
  const sp = must(await api('GET', '/api/v1/me/style-profile', { token }), 'get style profile').style_profile;
  if (!sp || sp.height_cm !== acct.height || sp.weight_kg !== acct.weight) {
    must(await api('PUT', '/api/v1/me/style-profile', {
      token,
      body: { fit_preference: null, height_cm: acct.height, weight_kg: acct.weight, usual_top_size: null, usual_bottom_size: null, shoe_size: null, style_codes: [], color_codes: [], brand_ids: [] },
    }), 'put style profile');
  }
}

async function ensureAddress(token, acct) {
  const list = must(await api('GET', '/api/v1/me/addresses', { token }), 'list addresses').addresses || [];
  let address = list.find((a) => a.label === 'Nhà');
  if (!address) {
    const provinces = must(await api('GET', '/api/v1/locations/provinces', { token }), 'provinces');
    const province = (provinces.provinces || provinces.items || provinces)[0];
    const wards = must(await api('GET', `/api/v1/locations/provinces/${province.code}/wards`, { token }), 'wards');
    const ward = (wards.wards || wards.items || wards)[0];
    address = must(await api('POST', '/api/v1/me/addresses', {
      token, body: { label: 'Nhà', recipient_name: acct.name, recipient_phone: acct.phone, address_line: '12 Nguyễn Huệ', ward_code: ward.code },
    }), 'create address');
  }
  if (!address.is_default) must(await api('PUT', `/api/v1/me/addresses/${address.address_id}/default`, { token }), 'set default address');
}

async function pickVariants(count, offset) {
  const res = must(await api('GET', '/commerce/api/v1/products', { query: { size: '40' } }), 'products');
  const variants = [];
  for (const product of res.items) {
    const v = product.variants.find((x) => x.variant_status === 'ACTIVE' && x.available_quantity > 0);
    if (v) variants.push(v.variant_id);
  }
  if (variants.length < count + offset) throw new Error('Not enough catalog variants to build outfits. Seed the catalog first.');
  return variants.slice(offset, offset + count);
}

async function listOutfits(token) {
  const out = [];
  for (let page = 0; page < 10; page++) {
    const r = must(await api('GET', '/wardrobe-community/api/v1/me/outfits', { token, query: { page: String(page), size: '50' } }), 'list outfits');
    out.push(...r.items);
    if (page + 1 >= (r.total_pages || 1)) break;
  }
  return out;
}

async function ensureOutfit(token, name, visibility, variantIds) {
  let outfit = (await listOutfits(token)).find((o) => o.name === name);
  if (!outfit) {
    outfit = must(await api('POST', '/wardrobe-community/api/v1/me/outfits', {
      token, body: { name, creation_source: 'MANUAL', items: variantIds.map((variant_id) => ({ variant_id })) },
    }), 'create outfit ' + name);
  }
  const base = `/wardrobe-community/api/v1/me/outfits/${outfit.outfit_id}`;
  const sharing = must(await api('GET', base + '/sharing', { token }), 'get sharing');
  let publicUrl = '';
  if (visibility === 'PUBLIC') {
    let t = sharing.public_enabled ? sharing.public_token : null;
    if (!t) t = must(await api('PUT', base + '/public-link', { token }), 'enable public link').public_token;
    publicUrl = `${FRONTEND}/shared/outfits/${encodeURIComponent(t)}`;
  } else if (sharing.visibility !== visibility) {
    must(await api('PATCH', base + '/sharing', { token, body: { visibility } }), 'set visibility ' + visibility);
  }
  return { id: outfit.outfit_id, publicUrl };
}

async function ensureFriends(tokenA, tokenB, idA, idB) {
  const friendsOf = async (t) => must(await api('GET', '/wardrobe-community/api/v1/me/friends', { token: t, query: { size: '100' } }), 'friends').items || [];
  if ((await friendsOf(tokenA)).some((f) => f.friend_account_id === idB)) return 'already friends';
  must(await api('POST', '/wardrobe-community/api/v1/me/friend-invitations', { token: tokenA, body: { recipient_email: ACCOUNTS.binh.email } }), 'invite');
  const incoming = must(await api('GET', '/wardrobe-community/api/v1/me/friend-invitations/incoming', { token: tokenB, query: { size: '50' } }), 'incoming').items || [];
  const inv = incoming.find((i) => i.sender_account_id === idA);
  if (!inv) throw new Error('Binh does not see the invitation from An (invitation e-mail/step failed)');
  must(await api('POST', `/wardrobe-community/api/v1/me/friend-invitations/${inv.friend_invitation_id}/accept`, { token: tokenB, body: {} }), 'accept invitation');
  return 'friends now';
}

function readBootstrapAdmin() {
  try {
    const env = fs.readFileSync(path.join(__dirname, '..', '.env'), 'utf8');
    const get = (k) => (env.match(new RegExp('^' + k + '=(.*)$', 'm')) || [])[1];
    const email = (get('STYLEMIND_BOOTSTRAP_ADMIN_EMAIL') || '').trim();
    const password = (get('STYLEMIND_BOOTSTRAP_ADMIN_PASSWORD') || '').trim();
    return email && password ? { email, password } : null;
  } catch { return null; }
}

async function ensureAdmin() {
  const boot = readBootstrapAdmin();
  if (!boot) return 'skipped: no bootstrap admin credentials in .env (see scripts/TEST_ACCOUNTS.md)';
  const adminLogin = await api('POST', '/api/v1/auth/login', { body: { email: boot.email, password: boot.password } });
  if (adminLogin.status !== 200) return 'skipped: bootstrap admin login failed (HTTP ' + adminLogin.status + ')';
  const { token: userToken } = await ensureAccount(ADMIN_EMAIL);
  const accountId = subjectOf(userToken);
  const adminToken = adminLogin.json.access_token;
  const found = must(await api('GET', '/api/v1/admin/users', { token: adminToken, query: { email_contains: ADMIN_EMAIL } }), 'find admin user').items.find((u) => u.email === ADMIN_EMAIL);
  if (found && found.roles.includes('ADMIN')) return 'already ADMIN';
  must(await api('PUT', `/api/v1/admin/users/${accountId}/roles`, { token: adminToken, body: { roles: ['CUSTOMER', 'ADMIN'] } }), 'promote admin');
  return 'promoted to ADMIN';
}

(async () => {
  if (PASSWORD.length < 12) throw new Error('SEED_PASSWORD must have at least 12 characters');
  const health = await fetch(GATEWAY + '/actuator/health').catch(() => null);
  if (!health || !health.ok) throw new Error('Gateway is not reachable at ' + GATEWAY);

  const ctx = {};
  for (const [key, acct] of Object.entries(ACCOUNTS)) {
    const { token, created } = await ensureAccount(acct.email);
    await ensureProfile(token, acct);
    await ensureAddress(token, acct);
    ctx[key] = { token, id: subjectOf(token) };
    log(`${acct.email}: ${created ? 'created' : 'exists'}, profile + default address ok`);
  }
  log('An <-> Binh: ' + (await ensureFriends(ctx.an.token, ctx.binh.token, ctx.an.id, ctx.binh.id)));

  const variants = await pickVariants(9, 0);
  await ensureOutfit(ctx.an.token, 'An - Riêng tư', 'PRIVATE', variants.slice(0, 3));
  await ensureOutfit(ctx.an.token, 'An - Bạn bè', 'FRIENDS', variants.slice(3, 6));
  const pub = await ensureOutfit(ctx.binh.token, 'Bình - Công khai', 'PUBLIC', variants.slice(6, 9));
  log('Outfits: An - Riêng tư (PRIVATE), An - Bạn bè (FRIENDS), Bình - Công khai (PUBLIC)');
  const adminStatus = await ensureAdmin();

  console.log('\n=== Test accounts (local only) ===');
  console.log('Password for all: ' + PASSWORD);
  console.table([
    { email: 'an@sm.test', role: 'customer', note: 'An 165cm/52kg, default address, friend of Binh, 2 outfits' },
    { email: 'binh@sm.test', role: 'customer', note: 'Binh 175cm/68kg, default address, 1 public outfit' },
    { email: 'chi@sm.test', role: 'customer', note: 'Chi 158cm/48kg, default address, no friends, no outfits' },
    { email: ADMIN_EMAIL, role: 'admin', note: adminStatus },
  ]);
  console.log('Public outfit link: ' + pub.publicUrl);
})().catch((e) => { console.error('\nSEED FAILED: ' + e.message); process.exit(1); });
