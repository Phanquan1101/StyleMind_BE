// Local structural/script syntax checks only; does not execute HTTP requests.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const path = require('node:path');

const file = path.join(__dirname, '../postman/StyleMind-Guest-Checkout.postman_collection.json');
const collection = JSON.parse(fs.readFileSync(file, 'utf8'));
assert.equal(collection.info.schema, 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json');
assert.equal(collection.auth.type, 'noauth');
assert.equal(collection.item.length, 9);
const variables = new Map(collection.variable.map(variable => [variable.key, variable.value]));
for (const name of ['guest_session_key', 'other_guest_session_key', 'checkout_key', 'variant_id', 'order_id']) {
  assert.equal(variables.get(name), '', `${name} must not contain saved credentials or data IDs`);
}
let scripts = 0;
function events(events) {
  for (const event of events || []) {
    new vm.Script(event.script.exec.join('\n'));
    scripts++;
  }
}
events(collection.event);
for (const item of collection.item) {
  assert.ok(['GET', 'POST'].includes(item.request.method));
  assert.ok(item.request.url.raw.startsWith('{{base_url}}/api/v1/'));
  assert.ok(item.request.header.every(header => header.key !== 'Authorization'));
  if (item.request.body) JSON.parse(item.request.body.raw);
  events(item.event);
}
assert.ok(collection.item.some(item => item.request.url.raw.endsWith('/payment-attempt')));
console.log(`PASS: ${collection.item.length} requests; ${scripts} scripts compile. Structural checks only; no HTTP execution.`);
