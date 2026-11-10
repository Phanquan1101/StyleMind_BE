'use strict';

// Maintainer-only, read-only export. Bootstrap never calls this or Gemini.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const root = path.resolve(__dirname, '..');
const output = path.join(root, 'demo', 'ai-stylist');
const read = name => JSON.parse(fs.readFileSync(path.join(root, name), 'utf8').replace(/^\uFEFF/, ''));
const sha = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
const shaCanonicalText = bytes => sha(Buffer.from(bytes.toString('utf8').replace(/\r\n/g, '\n'), 'utf8'));
const vectorSha = vector => { const bytes = Buffer.alloc(vector.length * 4); vector.forEach((n, i) => bytes.writeFloatLE(n, i * 4)); return sha(bytes); };

async function main() {
  const origin = new URL(process.argv[2] || 'http://127.0.0.1:6333');
  if (!['127.0.0.1', 'localhost'].includes(origin.hostname) || origin.protocol !== 'http:' || origin.username || origin.password) throw Error('Only local read-only Qdrant export is permitted');
  const evidence = read('reports/gemini-10-product-demo/real10-embedding-verification.json');
  const selection = read('reports/gemini-10-product-demo/development-seed-distinct10-selection.json');
  const headers = { 'content-type': 'application/json' };
  if (process.env.STYLEMIND_STYLIST_QDRANT_API_KEY) headers['api-key'] = process.env.STYLEMIND_STYLIST_QDRANT_API_KEY;
  async function request(route, body) {
    const response = await fetch(new URL(route, origin), { method: body ? 'POST' : 'GET', headers, body: body && JSON.stringify(body), signal: AbortSignal.timeout(10000) });
    if (!response.ok) throw Error('Qdrant export HTTP ' + response.status);
    return (await response.json()).result;
  }
  const physical = evidence.physical_collection;
  const info = await request('/collections/' + physical);
  if (info.config.params.vectors.size !== 1536 || info.config.params.vectors.distance !== 'Cosine') throw Error('Unexpected collection shape');
  const points = []; let offset;
  do {
    const page = await request('/collections/' + physical + '/points/scroll', { limit: 100, with_payload: true, with_vector: true, ...(offset ? { offset } : {}) });
    points.push(...page.points); offset = page.next_page_offset;
  } while (offset);
  points.sort((a, b) => a.id.localeCompare(b.id));
  const candidates = points.filter(p => p.payload.record_type === 'CATALOG_CANDIDATE');
  if (points.length !== 11 || candidates.length !== 10 || new Set(candidates.map(p => p.payload.product_id)).size !== 10) throw Error('Expected exactly ten distinct products plus manifest');
  for (const point of candidates) {
    const expected = evidence.vectors.find(v => v.variant_id === point.id);
    if (!expected || expected.product_id !== point.payload.product_id || expected.vector_sha256 !== vectorSha(point.vector)) throw Error('Vector differs from recorded REAL embedding evidence: ' + point.id);
  }
  if (new Set(candidates.map(p => vectorSha(p.vector))).size !== 10) throw Error('Duplicate vectors');
  fs.mkdirSync(output, { recursive: true });
  const write = (name, value) => fs.writeFileSync(path.join(output, name), typeof value === 'string' ? value : JSON.stringify(value, null, 2) + '\n', 'utf8');
  write('qdrant-points.json', { format_version: 1, source_collection: physical, dimensions: 1536, distance: 'Cosine', points });
  write('commerce-manifest.json', { source: 'commerce-service/src/main/resources/db/seed/development-catalog.json', source_sha256: selection.source_sha256, categories: selection.categories, brands: selection.brands, products: selection.products });
  const sql = fs.readFileSync(path.join(root, 'reports/gemini-10-product-demo/development-seed-distinct10.sql'), 'utf8').replace(/(INSERT INTO[^\r\n]+);/g, '$1 ON CONFLICT DO NOTHING;');
  write('commerce-seed.sql', sql);
  fs.copyFileSync(path.join(root, 'reports/gemini-10-product-demo/catalog-policy-distinct10.local-demo.json'), path.join(output, 'catalog-policy.json'));
  // Preserve original fixture metadata verbatim; this is not human-reviewed fashion knowledge.
  fs.copyFileSync(path.join(root, 'stylist-service/local-runtime/knowledge-artifact.json'), path.join(output, 'knowledge-artifact.json'));
  const files = ['qdrant-points.json', 'commerce-manifest.json', 'commerce-seed.sql', 'catalog-policy.json', 'knowledge-artifact.json'];
  write('bundle.json', {
    version: 1, scope: 'PRIVATE_STYLEMIND_TEAM_LOCAL_DEMO',
    embedding_provenance: 'reports/gemini-10-product-demo/real10-embedding-verification.json',
    embedding_provenance_sha256: shaCanonicalText(fs.readFileSync(path.join(root, 'reports/gemini-10-product-demo/real10-embedding-verification.json'))),
    provider: 'gemini', model: 'gemini-embedding-001', dimensions: 1536, distance: 'Cosine',
    product_count: 10, top_count: 5, bottom_count: 5, distinct_vectors: 10,
    knowledge_kind: 'EXISTING_SYNTHETIC_DEMO_FIXTURE',
    knowledge_source: 'stylist-service/scripts/wave2-disposable-gateway-harness.cjs:writeArtifacts',
    knowledge_review_note: 'Original synthetic-fixture-only metadata preserved. No claim of human-reviewed fashion knowledge.',
    distribution_note: 'Owner-authorized private team demo only; no grant of public redistribution rights for underlying catalog/images.',
    files: Object.fromEntries(files.map(name => [name, shaCanonicalText(fs.readFileSync(path.join(output, name)))])),
    vector_hashes: Object.fromEntries(candidates.map(p => [p.id, vectorSha(p.vector)]))
  });
  console.log('EXPORTED: 10 recorded REAL Gemini vectors, 5 TOP + 5 BOTTOM, no provider calls');
}
main().catch(e => { console.error(e.message); process.exitCode = 1; });
