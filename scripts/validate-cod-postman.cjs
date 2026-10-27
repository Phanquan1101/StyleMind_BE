const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const collection = JSON.parse(fs.readFileSync(path.join(__dirname, '../postman/StyleMind-COD-Two-Step.postman_collection.json'), 'utf8'));
assert.equal(collection.info.schema, 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json');
let requests = 0, scripts = 0;
for (const item of collection.item) {
  assert.ok(item.request.method && item.request.url); requests++;
  if (item.request.body) JSON.parse(item.request.body.raw.replace(/{{amountVnd}}/g, '125000').replace(/{{[^}]+}}/g, 'test-only'));
  for (const event of item.event || []) { new vm.Script(event.script.exec.join('\n')); scripts++; }
}
assert.equal(requests, 11); assert.equal(scripts, 11);
for (const variable of collection.variable) if (/Token$/.test(variable.key)) assert.equal(variable.value, '');
console.log(JSON.stringify({ requests, scripts, structuralValidation: 'PASS', httpRuntime: 'NOT EXECUTED' }));
