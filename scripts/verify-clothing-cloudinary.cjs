const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const { buildJobs, rewriteProducts } = require('./upload-clothing-200.cjs');
const directory = path.resolve(__dirname, '../artifacts/catalog-review/livostyle');
async function main() {
  const read = name => JSON.parse(fs.readFileSync(path.join(directory, name), 'utf8'));
  const sourceBytes = fs.readFileSync(path.join(directory, 'clothing-200.json'));
  const products = JSON.parse(sourceBytes.toString('utf8'));
  const checkpoint = read('cloudinary-upload.json');
  const summary = read('cloudinary-summary.json');
  const output = read('clothing-200-cloudinary.json');
  const jobs = buildJobs(products, checkpoint.public_id_prefix);
  assert.equal(summary.status, 'COMPLETE');
  assert.equal(checkpoint.source_sha256, crypto.createHash('sha256').update(sourceBytes).digest('hex'));
  assert.equal(checkpoint.assets.length, jobs.length);
  assert.equal(new Set(checkpoint.assets.map(a => a.source_url)).size, jobs.length);
  assert.equal(new Set(checkpoint.assets.map(a => a.public_id)).size, jobs.length);
  assert.equal(checkpoint.failures.length, 0);
  const byUrl = new Map(checkpoint.assets.map(a => [a.source_url, a]));
  for (const job of jobs) {
    const asset = byUrl.get(job.source_url);
    assert.ok(asset);
    assert.equal(asset.public_id, job.public_id);
    const url = new URL(asset.secure_url);
    assert.equal(url.origin, 'https://res.cloudinary.com');
    assert.ok(decodeURIComponent(url.pathname).startsWith(`/${checkpoint.cloud_name}/image/upload/v${asset.version}/${asset.public_id}.`));
    assert.ok(Number.isSafeInteger(asset.bytes) && asset.bytes > 0);
    assert.ok(Number.isSafeInteger(asset.width) && asset.width > 0);
    assert.ok(Number.isSafeInteger(asset.height) && asset.height > 0);
  }
  assert.deepEqual(output, rewriteProducts(products, checkpoint.assets));
  assert.equal(output.length, 200);
  assert.equal(summary.uploaded_images, jobs.length);
  console.log(JSON.stringify({ structural_validation: 'PASS', products: output.length, images: jobs.length, source_records_preserved: true }));
  if (!process.argv.includes('--http')) return;
  const results = [];
  let cursor = 0;
  async function worker() {
    while (cursor < checkpoint.assets.length) {
      const asset = checkpoint.assets[cursor++];
      let result;
      for (let attempt = 0; attempt < 2; attempt++) {
        try {
          const response = await fetch(asset.secure_url, { method: 'HEAD', redirect: 'error', signal: AbortSignal.timeout(15000) });
          const type = response.headers.get('content-type') || '';
          result = { public_id: asset.public_id, status: response.status, content_type: type, ok: response.ok && type.startsWith('image/') };
        } catch { result = { public_id: asset.public_id, status: null, ok: false, code: 'NETWORK_OR_TIMEOUT' }; }
        if (result.ok) break;
      }
      results.push(result);
      if (results.length % 100 === 0) console.log(JSON.stringify({ http_checked: results.length, total: checkpoint.assets.length, failed: results.filter(r => !r.ok).length }));
    }
  }
  await Promise.all(Array.from({ length: 6 }, worker));
  results.sort((a, b) => a.public_id < b.public_id ? -1 : a.public_id > b.public_id ? 1 : 0);
  const report = { checked_at: new Date().toISOString(), structural_validation: 'PASS', expected_images: jobs.length,
    checked: results.length, passed: results.filter(r => r.ok).length, failed: results.filter(r => !r.ok).length,
    method: 'HEAD', results };
  fs.writeFileSync(path.join(directory, 'cloudinary-http-verification.json'), JSON.stringify(report, null, 2) + '\n');
  console.log(JSON.stringify({ http_checked: report.checked, passed: report.passed, failed: report.failed }));
  if (report.failed) process.exitCode = 1;
}
main().catch(() => { console.error('CLOUDINARY_VERIFICATION_FAILED'); process.exitCode = 1; });
