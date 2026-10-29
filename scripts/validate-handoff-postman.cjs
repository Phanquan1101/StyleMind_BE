// Structural/syntax checks only; no requests or remote JSON schema validation.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const path = require('node:path');
const file = path.join(__dirname, '../postman/StyleMind-Commerce-Payment-Handoff.postman_collection.json');
const collection = JSON.parse(fs.readFileSync(file, 'utf8'));
assert.equal(collection.info.schema, 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json');
let requests = 0;
let scripts = 0;
function visit(items) {
  for (const item of items) {
    if (item.item) visit(item.item);
    if (item.request) {
      requests++;
      assert.ok(item.request.method);
      assert.ok(item.request.url);
      if (item.request.body?.mode === 'raw') {
        const substituted = item.request.body.raw.replace(/{{amountVnd}}/g, '125000')
          .replace(/{{[^}]+}}/g, 'test-value');
        JSON.parse(substituted);
      }
    }
    for (const event of item.event || []) {
      new vm.Script(event.script.exec.join('\n'));
      scripts++;
    }
  }
}
visit(collection.item);
assert.equal(requests, 8);
assert.equal(scripts, 8);
for (const variable of collection.variable) {
  if (/Token$/.test(variable.key)) assert.equal(variable.value, '');
}
console.log(JSON.stringify({ requests, scripts, structuralValidation: 'PASS', httpRuntime: 'NOT EXECUTED' }));
