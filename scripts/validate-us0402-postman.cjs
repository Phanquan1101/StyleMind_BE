const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const collection = JSON.parse(fs.readFileSync(path.join(__dirname, '../postman/StyleMind-US-04.02-Confirm-Payment.postman_collection.json'), 'utf8'));
assert.equal(collection.info.schema, 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json');
let requests = 0, scripts = 0;
for (const item of collection.item) {
  assert.ok(item.request.method && item.request.url); requests++;
  if (item.request.body) JSON.parse(item.request.body.raw
    .replace(/{{(?:caseEventId|confirmedProviderEventId|amountVnd)}}/g, '125000')
    .replace(/{{[^}]+}}/g, 'test-only-value'));
  for (const event of item.event || []) { new vm.Script(event.script.exec.join('\n')); scripts++; }
}
assert.equal(requests, 9); assert.equal(scripts, 15);
for (const variable of collection.variable) if (/Token$|ApiKey$|Secret$/.test(variable.key)) assert.equal(variable.value, '');
console.log(JSON.stringify({ requests, scripts, structuralValidation: 'PASS', httpRuntime: 'NOT EXECUTED' }));

// Execute pre-request scripts with synthetic values; this is not a Postman HTTP run.
const crypto = require('node:crypto');
for (const mode of ['hmac', 'api-key']) for (const index of [0, 1, 2, 4, 5]) {
  const values = Object.fromEntries(collection.variable.map(v => [v.key, v.value]));
  Object.assign(values, { webhookAuthMode: mode, webhookSecret: 'synthetic-hmac-only', sepayApiKey: 'synthetic-key-only',
    paymentReference: 'SMTEST', wrongAmountReference: 'SMWRONG', latePaymentReference: 'SMLATE', confirmedProviderEventId: '123' });
  const headers = new Map();
  const resolve = s => s.replace(/{{([^}]+)}}/g, (_, key) => values[key]);
  const variables = { get: key => values[key], set: (key, value) => { values[key] = value; }, replaceIn: resolve };
  const pm = { variables, collectionVariables: variables, request: { body: collection.item[index].request.body,
    headers: { remove: key => headers.delete(key), upsert: ({ key, value }) => headers.set(key, value) } } };
  const CryptoJS = { enc: { Hex: {} }, HmacSHA256: (message, secret) => ({
    toString: () => crypto.createHmac('sha256', secret).update(message).digest('hex') }) };
  for (const event of collection.item[index].event.filter(e => e.listen === 'prerequest'))
    vm.runInNewContext(event.script.exec.join('\n'), { pm, CryptoJS, Date });
  JSON.parse(resolve(pm.request.body.raw));
  if (mode === 'hmac') {
    assert.equal(headers.has('Authorization'), false);
    assert.equal(headers.get('X-SePay-Signature'), 'sha256=' + crypto.createHmac('sha256', values.webhookSecret)
      .update(headers.get('X-SePay-Timestamp') + '.' + resolve(pm.request.body.raw)).digest('hex'));
  } else assert.equal(headers.get('Authorization'), 'Apikey synthetic-key-only');
}
console.log('Pre-request HMAC/API-key fixtures: PASS; Postman HTTP runtime NOT EXECUTED');
