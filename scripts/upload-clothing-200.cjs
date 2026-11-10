const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const root = path.resolve(__dirname, '..');
const directory = path.join(root, 'artifacts/catalog-review/livostyle');

function envValue(name) {
  if (process.env[name]) return process.env[name];
  const file = path.join(root, '.env');
  if (!fs.existsSync(file)) return '';
  const match = fs.readFileSync(file, 'utf8').match(new RegExp(`^\\s*${name}\\s*=\\s*(.*?)\\s*$`, 'm'));
  if (!match) return '';
  const value = match[1];
  return /^(['"]).*\1$/.test(value) ? value.slice(1, -1) : value.replace(/\s+#.*$/, '');
}
function configFrom(raw, folder) {
  try {
    const url = new URL(raw);
    if (url.protocol !== 'cloudinary:' || !/^[a-z0-9_-]+$/i.test(url.hostname) || !url.username || !url.password) throw Error();
    const base = (folder || 'stylemind/products').replace(/^\/+|\/+$/g, '');
    if (!/^[a-z0-9_-]+(?:\/[a-z0-9_-]+)*$/i.test(base)) throw Error();
    return { cloud: url.hostname, key: decodeURIComponent(url.username), secret: decodeURIComponent(url.password), folder: `${base}/livostyle-200` };
  } catch { throw new Error('CLOUDINARY_CONFIG_INVALID'); }
}
function sign(parameters, secret) {
  return crypto.createHash('sha1').update(Object.keys(parameters).sort().map(k => `${k}=${parameters[k]}`).join('&') + secret).digest('hex');
}
function buildJobs(products, folder) {
  const jobs = new Map();
  if (products.length !== 200 || new Set(products.map(p => p.id)).size !== 200) throw new Error('EXPECTED_200_UNIQUE_PRODUCTS');
  for (const p of products) {
    if (!/^\d+$/.test(p.id) || !p.images?.length) throw new Error('INVALID_SOURCE_PRODUCT');
    for (const image of p.images) {
      let url;
      try { url = new URL(image.url); } catch { throw new Error('INVALID_SOURCE_IMAGE_URL'); }
      if (url.protocol !== 'https:' || url.hostname !== 'cdn.shopify.com' || url.username || url.password || url.port) throw new Error('INVALID_SOURCE_IMAGE_URL');
      const hash = crypto.createHash('sha256').update(image.url).digest('hex');
      if (!jobs.has(image.url)) jobs.set(image.url, { source_url: image.url, public_id: `${folder}/${p.id}/${hash}`, source_product_id: p.id });
    }
    if (!jobs.has(p.featured_image_url)) throw new Error('FEATURED_IMAGE_NOT_IN_GALLERY');
  }
  return [...jobs.values()];
}
function validateAsset(data, job, config) {
  const invalid = () => { throw new Error('CLOUDINARY_RESPONSE_INVALID'); };
  if (data.public_id !== job.public_id || data.resource_type !== 'image' || data.type !== 'upload'
      || !Number.isSafeInteger(data.version) || data.width <= 0 || data.height <= 0 || data.bytes <= 0) invalid();
  let url;
  try { url = new URL(data.secure_url); } catch { invalid(); }
  const expectedPath = `/${config.cloud}/image/upload/v${data.version}/${job.public_id}.`;
  if (url.origin !== 'https://res.cloudinary.com' || !decodeURIComponent(url.pathname).startsWith(expectedPath) || url.search || url.hash) invalid();
  const expectedSignature = sign({ public_id: data.public_id, version: data.version }, config.secret);
  if (typeof data.signature !== 'string' || !/^[a-f0-9]{40}$/.test(data.signature)
      || !crypto.timingSafeEqual(Buffer.from(data.signature, 'hex'), Buffer.from(expectedSignature, 'hex'))) invalid();
  return { ...job, secure_url: data.secure_url, asset_id: data.asset_id, version: data.version,
    width: data.width, height: data.height, bytes: data.bytes, format: data.format };
}
async function uploadOne(job, config, fetcher = fetch) {
  const parameters = { overwrite: 'false', public_id: job.public_id, timestamp: String(Math.floor(Date.now() / 1000)), unique_filename: 'false' };
  const body = new FormData();
  for (const [k, v] of Object.entries(parameters)) body.set(k, v);
  body.set('file', job.source_url); body.set('api_key', config.key); body.set('signature', sign(parameters, config.secret));
  let response;
  try {
    response = await fetcher(`https://api.cloudinary.com/v1_1/${config.cloud}/image/upload`, {
      method: 'POST', body, redirect: 'error', signal: AbortSignal.timeout(45000)
    });
  } catch { throw new Error('CLOUDINARY_NETWORK_OR_TIMEOUT'); }
  if (!response.ok) throw new Error(`CLOUDINARY_HTTP_${response.status}`);
  let data;
  try { data = await response.json(); } catch { throw new Error('CLOUDINARY_RESPONSE_INVALID'); }
  return validateAsset(data, job, config);
}
function writeJson(file, data) {
  fs.writeFileSync(`${file}.tmp`, JSON.stringify(data, null, 2) + '\n');
  fs.renameSync(`${file}.tmp`, file);
}
function rewriteProducts(products, assets) {
  const byUrl = new Map(assets.map(a => [a.source_url, a.secure_url]));
  return products.map(p => {
    if (!byUrl.has(p.featured_image_url) || p.images.some(i => !byUrl.has(i.url))) throw new Error('INCOMPLETE_IMAGE_MAPPING');
    return { ...p, featured_image_url: byUrl.get(p.featured_image_url),
      images: p.images.map(i => ({ ...i, url: byUrl.get(i.url) })) };
  });
}
async function main(args = process.argv.slice(2)) {
  if (args.some(a => a !== '--upload' && !/^--limit=\d+$/.test(a))) throw new Error('INVALID_ARGUMENT');
  const config = configFrom(envValue('CLOUDINARY_URL'), envValue('CLOUDINARY_FOLDER'));
  const input = fs.readFileSync(path.join(directory, 'clothing-200.json'));
  const products = JSON.parse(input.toString('utf8'));
  const sourceHash = crypto.createHash('sha256').update(input).digest('hex');
  const jobs = buildJobs(products, config.folder);
  if (!args.includes('--upload')) {
    console.log(JSON.stringify({ status: 'DRY_RUN', products: products.length, unique_images: jobs.length, public_id_prefix: config.folder })); return;
  }
  const limitArg = args.find(a => a.startsWith('--limit='));
  const limit = limitArg ? Number(limitArg.split('=')[1]) : jobs.length;
  if (!Number.isSafeInteger(limit) || limit < 1) throw new Error('INVALID_LIMIT');
  const lock = path.join(directory, 'cloudinary-upload.lock');
  let lockFd;
  try { lockFd = fs.openSync(lock, 'wx'); } catch { throw new Error('UPLOAD_LOCK_EXISTS'); }
  try {
    const checkpointFile = path.join(directory, 'cloudinary-upload.json');
    const checkpoint = fs.existsSync(checkpointFile) ? JSON.parse(fs.readFileSync(checkpointFile, 'utf8'))
      : { source_sha256: sourceHash, cloud_name: config.cloud, public_id_prefix: config.folder, assets: [], failures: [] };
    if (checkpoint.source_sha256 !== sourceHash || checkpoint.cloud_name !== config.cloud || checkpoint.public_id_prefix !== config.folder) throw new Error('CHECKPOINT_SOURCE_OR_ACCOUNT_MISMATCH');
    const jobByUrl = new Map(jobs.map(j => [j.source_url, j]));
    const done = new Set();
    for (const asset of checkpoint.assets) {
      const job = jobByUrl.get(asset.source_url);
      if (!job || asset.public_id !== job.public_id || done.has(asset.source_url)) throw new Error('CHECKPOINT_INVALID');
      const url = new URL(asset.secure_url);
      if (url.origin !== 'https://res.cloudinary.com' || !decodeURIComponent(url.pathname).startsWith(`/${config.cloud}/image/upload/v${asset.version}/${job.public_id}.`)) throw new Error('CHECKPOINT_INVALID');
      done.add(asset.source_url);
    }
    const pending = jobs.filter(j => !done.has(j.source_url)).slice(0, limit);
    checkpoint.failures = [];
    let cursor = 0, fatal = false;
    async function worker() {
      while (!fatal && cursor < pending.length) {
        const job = pending[cursor++];
        let asset, error;
        for (let attempt = 0; attempt < 3; attempt++) {
          try { asset = await uploadOne(job, config); break; }
          catch (e) {
            error = e.message;
            if (!/^CLOUDINARY_(NETWORK_OR_TIMEOUT|HTTP_(429|5\d\d))$/.test(error)) break;
            if (attempt < 2) await new Promise(resolve => setTimeout(resolve, 1000 * (attempt + 1)));
          }
        }
        if (asset) { checkpoint.assets.push(asset); done.add(job.source_url); }
        else {
          checkpoint.failures.push({ public_id: job.public_id, code: error });
          if (/HTTP_(401|403)/.test(error) || checkpoint.failures.length >= 5) fatal = true;
        }
        checkpoint.updated_at = new Date().toISOString();
        writeJson(checkpointFile, checkpoint);
        if (checkpoint.assets.length % 25 === 0 || !asset) console.log(JSON.stringify({ uploaded: checkpoint.assets.length, total: jobs.length, failures: checkpoint.failures.length, ...(asset ? {} : { code: error }) }));
      }
    }
    await Promise.all(Array.from({ length: 3 }, worker));
    const complete = jobs.every(j => done.has(j.source_url));
    const summary = { status: complete ? 'COMPLETE' : 'PARTIAL', products: products.length, expected_images: jobs.length,
      uploaded_images: checkpoint.assets.length, failures: checkpoint.failures, source_sha256: sourceHash,
      uploaded_bytes: checkpoint.assets.reduce((n, a) => n + a.bytes, 0), database_touched: false,
      visual_matching_verified: false, updated_at: new Date().toISOString() };
    if (complete) writeJson(path.join(directory, 'clothing-200-cloudinary.json'), rewriteProducts(products, checkpoint.assets));
    writeJson(path.join(directory, 'cloudinary-summary.json'), summary);
    console.log(JSON.stringify(summary));
    if (checkpoint.failures.length) process.exitCode = 1;
  } finally { fs.closeSync(lockFd); fs.unlinkSync(lock); }
}
module.exports = { configFrom, sign, buildJobs, validateAsset, uploadOne, rewriteProducts };
if (require.main === module) main().catch(e => {
  // Never print provider bodies, raw URLs containing credentials, or stack traces.
  console.error(/^[A-Z][A-Z0-9_]+$/.test(e.message) ? e.message : 'UPLOAD_FAILED_CHECK_LOCAL_INPUT');
  process.exitCode = 1;
});
