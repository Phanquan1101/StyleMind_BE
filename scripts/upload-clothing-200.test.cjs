const { test } = require('node:test');
const assert = require('node:assert/strict');
const { configFrom, sign, buildJobs, validateAsset, uploadOne, rewriteProducts } = require('./upload-clothing-200.cjs');
const config = configFrom('cloudinary://test-key:test-secret@test-cloud', 'stylemind/products');
const products = Array.from({ length: 200 }, (_, i) => ({ id: String(i + 1), featured_image_url: `https://cdn.shopify.com/${i}.jpg`, images: [{ url: `https://cdn.shopify.com/${i}.jpg`, alt: 'preserved' }], variants: [{ sku: `SKU-${i}` }] }));
const jobs = buildJobs(products, config.folder);
function response(job = jobs[0]) {
  const version = 1;
  return { public_id: job.public_id, resource_type: 'image', type: 'upload', version, width: 1000, height: 1500,
    bytes: 1234, format: 'jpg', asset_id: 'asset-id', secure_url: `https://res.cloudinary.com/test-cloud/image/upload/v1/${job.public_id}.jpg`,
    signature: sign({ public_id: job.public_id, version }, config.secret) };
}
test('configuration rejects invalid input without echoing secrets', () => {
  for (const raw of ['', 'https://secret@example.com', 'cloudinary://only-key@cloud']) assert.throws(() => configFrom(raw), /^Error: CLOUDINARY_CONFIG_INVALID$/);
  assert.throws(() => configFrom('cloudinary://key:secret@cloud', '../escape'));
});
test('jobs are deterministic, isolate products, and only allow approved remote CDN', () => {
  assert.deepEqual(buildJobs(products, config.folder), jobs);
  assert.equal(new Set(jobs.map(j => j.public_id)).size, 200);
  for (const url of ['http://cdn.shopify.com/a', 'https://localhost/a', 'https://cdn.shopify.com.attacker.test/a', 'https://user:pass@cdn.shopify.com/a']) {
    const copy = structuredClone(products); copy[0].images[0].url = url;
    assert.throws(() => buildJobs(copy, config.folder), /INVALID_SOURCE_IMAGE_URL/);
  }
});
test('response validation rejects wrong identity, cloud, signature and non-images', () => {
  assert.equal(validateAsset(response(), jobs[0], config).public_id, jobs[0].public_id);
  for (const patch of [{ public_id: 'other' }, { secure_url: 'https://evil.test/image.jpg' }, { signature: '0'.repeat(40) }, { resource_type: 'video' }]) {
    assert.throws(() => validateAsset({ ...response(), ...patch }, jobs[0], config), /CLOUDINARY_RESPONSE_INVALID/);
  }
});
test('signed upload uses overwrite=false and never sends the secret', async () => {
  const asset = await uploadOne(jobs[0], config, async (url, options) => {
    assert.equal(url, 'https://api.cloudinary.com/v1_1/test-cloud/image/upload');
    assert.equal(options.body.get('overwrite'), 'false');
    assert.equal(options.body.get('file'), jobs[0].source_url);
    assert.ok(![...options.body.values()].includes(config.secret));
    return { ok: true, json: async () => response() };
  });
  assert.equal(asset.bytes, 1234);
});
test('network, HTTP auth and provider parsing failures are sanitized', async () => {
  await assert.rejects(uploadOne(jobs[0], config, async () => { throw Error('test-secret'); }), /^Error: CLOUDINARY_NETWORK_OR_TIMEOUT$/);
  await assert.rejects(uploadOne(jobs[0], config, async () => ({ ok: false, status: 401 })), /^Error: CLOUDINARY_HTTP_401$/);
  await assert.rejects(uploadOne(jobs[0], config, async () => ({ ok: true, json: async () => { throw Error('test-secret'); } })), /^Error: CLOUDINARY_RESPONSE_INVALID$/);
});
test('output preserves galleries, alt text, order and variants; refuses incomplete mapping', () => {
  const assets = jobs.map(j => validateAsset(response(j), j, config));
  const output = rewriteProducts(products, assets);
  assert.equal(output.length, 200);
  assert.equal(output[0].images[0].alt, 'preserved');
  assert.deepEqual(output[0].variants, products[0].variants);
  assert.equal(output[0].featured_image_url, assets[0].secure_url);
  assert.equal(products[0].images[0].url, jobs[0].source_url);
  assert.throws(() => rewriteProducts(products, assets.slice(1)), /INCOMPLETE_IMAGE_MAPPING/);
});
