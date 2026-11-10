'use strict';

// Dedicated local team demo. Reuses real service migrations, KnowledgeArtifactImporter,
// existing 10-product SQL and the existing Gemini HTTP fixture. No Google transport.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const net = require('node:net');
const { spawn } = require('node:child_process');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..', '..');
const dataDir = path.join(root, 'demo', 'ai-stylist');
const args = process.argv.slice(2);
const option = (key, fallback) => args.includes(key) ? args[args.indexOf(key) + 1] : fallback;
const project = option('--project', 'stylemind-ai-demo-team');
const offset = Number(option('--port-offset', '20000'));
if (!/^stylemind-ai-demo-[a-z0-9][a-z0-9-]{0,31}$/.test(project) || !Number.isInteger(offset) || offset < 0 || offset > 50000) throw Error('Invalid dedicated demo project/port offset');
const runtime = path.join(root, 'stylist-service', 'target', 'team-demo', project);
fs.mkdirSync(runtime, { recursive: true });
const read = file => JSON.parse(fs.readFileSync(file, 'utf8').replace(/^\uFEFF/, ''));
const save = (file, value) => fs.writeFileSync(file, JSON.stringify(value, null, 2) + '\n', { mode: 0o600 });
const sha = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
const vectorSha = vector => { const bytes = Buffer.alloc(vector.length * 4); vector.forEach((n, i) => bytes.writeFloatLE(n, i * 4)); return sha(bytes); };
const stateFile = path.join(runtime, 'state.json');
const secret = () => crypto.randomBytes(24).toString('base64url');
const state = fs.existsSync(stateFile) ? read(stateFile) : {
  project, offset, customerId: crypto.randomUUID(),
  passwords: { postgres: secret(), commerce: secret(), stylist: secret(), neo4j: secret() },
  ports: { postgres: 5433 + offset, qdrant: 6333 + offset, neo4j: 7687 + offset, commerce: 8082 + offset, stylist: 8085 + offset, gateway: 8080 + offset }, apps: {}
};
assert.equal(state.project, project);
if (!args.includes('--stop-apps')) assert.equal(state.offset, offset, 'Existing project uses a different port offset');
const flush = () => save(stateFile, state);
let javaCommand = 'java';
const javaArgs = (memory, label, jar) => ['-Xmx' + memory + 'm', '-Dserver.address=127.0.0.1', '-Dstylemind.demo.instance=' + runtime, '-Dlogging.file.name=' + path.join(runtime, label + '.log'), '-jar', jar];
const redact = value => Object.values(state.passwords).reduce((s, v) => s.split(v).join('[REDACTED]'), String(value));
const cleanEnv = () => Object.fromEntries(Object.entries(process.env).filter(([k]) => !/^(STYLEMIND_|SPRING_|DEMO_|CLOUDINARY_|JAVA_TOOL_OPTIONS$|_JAVA_OPTIONS$|JDK_JAVA_OPTIONS$)/i.test(k)));

function run(command, argv, { input, env, timeout = 180000, log } = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, argv, { cwd: root, env: { ...cleanEnv(), ...env }, windowsHide: true,
      windowsVerbatimArguments: process.platform === 'win32' && path.basename(command).toLowerCase() === 'cmd.exe',
      stdio: ['pipe', 'pipe', 'pipe'] });
    let output = '';
    const timer = setTimeout(() => { child.kill(); reject(Error(command + ' timed out')); }, timeout);
    for (const stream of [child.stdout, child.stderr]) stream.on('data', b => { output = (output + b.toString('utf8')).slice(-16000000); if (log) fs.writeFileSync(log, redact(output)); });
    child.once('error', e => { clearTimeout(timer); reject(e); });
    child.once('exit', code => { clearTimeout(timer); if (log) fs.writeFileSync(log, redact(output)); code === 0 ? resolve(output) : reject(Error(command + ' failed (' + code + ')\n' + redact(output.slice(-5000)))); });
    child.stdin.end(input || '');
  });
}
const composeFile = path.join(dataDir, 'compose.yml');
const envFile = path.join(runtime, 'compose.env');
const compose = argv => run('docker', ['compose', '--env-file', envFile, '-f', composeFile, '-p', project, ...argv], { timeout: 240000 });
async function http(url, { method = 'GET', body, token, expected = 200, timeout = 10000 } = {}) {
  const response = await fetch(url, { method, headers: { ...(body ? { 'content-type': 'application/json' } : {}), ...(token ? { authorization: 'Bearer ' + token } : {}) }, body: body && JSON.stringify(body), signal: AbortSignal.timeout(timeout) });
  const text = await response.text();
  let json; try { json = JSON.parse(text); } catch { json = {}; }
  if (response.status !== expected) throw Error(method + ' ' + new URL(url).pathname + ': HTTP ' + response.status + ' ' + (json.code || json.error_code || json.detail || ''));
  return json;
}
async function waitFor(label, test, timeout = 150000) {
  const until = Date.now() + timeout;
  while (Date.now() < until) { try { if (await test()) return; } catch {} await new Promise(r => setTimeout(r, 750)); }
  throw Error(label + ' not ready; inspect logs in ' + runtime);
}
async function freePort() { return new Promise((resolve, reject) => { const s = net.createServer(); s.on('error', reject); s.listen(0, '127.0.0.1', () => { const port = s.address().port; s.close(() => resolve(port)); }); }); }
function alive(pid) { try { process.kill(pid, 0); return true; } catch { return false; } }
async function owns(record) {
  if (!record || !alive(record.pid)) return false;
  if (process.platform !== 'win32') return fs.readFileSync('/proc/' + record.pid + '/cmdline', 'utf8').includes(record.marker);
  const output = await run('powershell.exe', ['-NoProfile', '-Command', '(Get-CimInstance Win32_Process -Filter "ProcessId=' + record.pid + '").CommandLine']);
  return output.includes(record.marker);
}
async function startApp(label, command, argv, env, health, marker = runtime) {
  if (await owns(state.apps[label])) { await waitFor(label, health, 15000); return; }
  const logFile = path.join(runtime, label + '.log');
  const consoleFile = /java(?:\.exe)?$/i.test(command) ? path.join(runtime, label + '.console.log') : logFile;
  const handle = fs.openSync(consoleFile, 'a');
  const child = spawn(command, argv, { cwd: root, env: { ...cleanEnv(), ...env }, detached: true, windowsHide: true, stdio: ['ignore', handle, handle] });
  await new Promise((resolve, reject) => { child.once('spawn', resolve); child.once('error', reject); });
  child.unref(); fs.closeSync(handle);
  state.apps[label] = { pid: child.pid, marker, log: logFile }; flush();
  await waitFor(label, health);
  assert(await owns(state.apps[label]), label + ' did not remain running as the owned demo process');
}
async function build(service) {
  const source = path.join(root, service);
  function files(dir) { return fs.readdirSync(dir, { withFileTypes: true }).flatMap(e => e.isDirectory() ? files(path.join(dir, e.name)) : [path.join(dir, e.name)]).sort(); }
  const sourceFiles = [path.join(source, 'pom.xml'), ...files(path.join(source, 'src'))];
  const hash = crypto.createHash('sha256'); for (const file of sourceFiles) { hash.update(path.relative(source, file)); hash.update(fs.readFileSync(file)); }
  // Keep source paths short enough for Windows Java/Maven; never replace a running JAR.
  const buildDir = path.join(root, 'target', 'aidemo', hash.digest('hex').slice(0, 12), service);
  const jar = path.join(buildDir, 'target', service + '-0.0.1-SNAPSHOT.jar');
  if (fs.existsSync(jar)) return jar;
  console.log('BUILD ' + service + ' (isolated output; existing JAR/classes untouched)');
  fs.mkdirSync(buildDir, { recursive: true });
  fs.copyFileSync(path.join(source, 'pom.xml'), path.join(buildDir, 'pom.xml'));
  fs.cpSync(path.join(source, 'src'), path.join(buildDir, 'src'), { recursive: true });
  const pom = path.join(buildDir, 'pom.xml');
  const log = path.join(runtime, service + '.build.log');
  if (process.platform === 'win32') await run(process.env.ComSpec || 'cmd.exe', ['/d', '/s', '/c', 'mvn -B -ntp -f "' + pom + '" -Dmaven.test.skip=true package'], { timeout: 360000, log });
  else await run('mvn', ['-B', '-ntp', '-f', pom, '-Dmaven.test.skip=true', 'package'], { timeout: 360000, log });
  assert(fs.existsSync(jar), 'Missing built JAR'); return jar;
}
function validateBundle() {
  const bundle = read(path.join(dataDir, 'bundle.json'));
  for (const [file, expected] of Object.entries(bundle.files)) assert.equal(sha(fs.readFileSync(path.join(dataDir, file))), expected, 'Bundle checksum mismatch: ' + file);
  const provenanceFile = path.join(root, bundle.embedding_provenance);
  assert.equal(sha(fs.readFileSync(provenanceFile)), bundle.embedding_provenance_sha256, 'REAL embedding provenance changed');
  const provenance = read(provenanceFile);
  const archive = read(path.join(dataDir, 'qdrant-points.json'));
  const candidates = archive.points.filter(p => p.payload.record_type === 'CATALOG_CANDIDATE');
  assert.equal(archive.dimensions, 1536); assert.equal(archive.distance, 'Cosine'); assert.equal(archive.points.length, 11); assert.equal(candidates.length, 10);
  assert.equal(new Set(candidates.map(p => p.payload.product_id)).size, 10);
  for (const p of candidates) {
    assert.equal(p.vector.length, 1536); assert(p.vector.every(Number.isFinite)); assert.equal(vectorSha(p.vector), bundle.vector_hashes[p.id]);
    const recorded = provenance.vectors.find(v => v.variant_id === p.id);
    assert(recorded && recorded.product_id === p.payload.product_id, 'ID missing from REAL embedding provenance');
    assert.equal(vectorSha(p.vector), recorded.vector_sha256, 'Vector differs from recorded REAL embedding');
  }
  assert.equal(new Set(candidates.map(p => vectorSha(p.vector))).size, 10);
  assert.equal(candidates.filter(p => p.payload.slot === 'TOP').length, 5); assert.equal(candidates.filter(p => p.payload.slot === 'BOTTOM').length, 5);
  const catalog = read(path.join(dataDir, 'commerce-manifest.json'));
  assert.equal(catalog.products.length, 10);
  for (const p of catalog.products) { assert.equal(p.variants.length, 1); const hit = candidates.find(v => v.id === p.variants[0].variantId); assert(hit && hit.payload.product_id === p.productId && hit.payload.category_id === p.categoryId, 'Commerce/Qdrant ID mismatch'); }
  return { bundle, archive, catalog };
}
async function restoreQdrant(archive) {
  const base = 'http://127.0.0.1:' + state.ports.qdrant;
  const name = archive.source_collection;
  const list = await http(base + '/collections');
  if (!list.result.collections.some(c => c.name === name)) {
    await http(base + '/collections/' + name, { method: 'PUT', body: { vectors: { size: 1536, distance: 'Cosine' } } });
    await http(base + '/collections/' + name + '/points?wait=true', { method: 'PUT', body: { points: archive.points } });
  }
  const info = (await http(base + '/collections/' + name)).result;
  assert.equal(info.config.params.vectors.size, 1536); assert.equal(info.config.params.vectors.distance, 'Cosine'); assert.equal(info.points_count, 11, 'Existing demo collection has unexpected data; no reset performed');
  const points = (await http(base + '/collections/' + name + '/points/scroll', { method: 'POST', body: { limit: 100, with_vector: true, with_payload: true } })).result.points;
  assert.equal(points.length, 11);
  for (const p of points) { const expected = archive.points.find(x => x.id === p.id); assert(expected, 'Unexpected Qdrant ID'); assert.equal(vectorSha(p.vector), vectorSha(expected.vector), 'Existing vector differs; refusing overwrite'); assert.deepEqual(p.payload, expected.payload, 'Existing payload differs; refusing overwrite'); }
  const alias = 'stylemind_stylist_runtime_gemini_team10';
  const existing = (await http(base + '/aliases')).result.aliases.find(a => a.alias_name === alias);
  if (existing) assert.equal(existing.collection_name, name, 'Alias already points elsewhere; no reset performed');
  else await http(base + '/collections/aliases', { method: 'POST', body: { actions: [{ create_alias: { collection_name: name, alias_name: alias } }] } });
  return alias;
}
async function pg(database, sql) {
  return compose(['exec', '-T', 'postgres', 'psql', '-U', 'demo_admin', '-d', database, '-At', '-v', 'ON_ERROR_STOP=1', '-c', sql]);
}
async function neoCount() {
  const query = "MATCH (a:KnowledgeArtifact {artifact_id:'synthetic-wave2-http-knowledge',version:'fixture-v1'}) OPTIONAL MATCH (a)-[:CONTAINS]->(e:KnowledgeEntry) RETURN count(DISTINCT a) AS artifacts,count(DISTINCT e) AS entries";
  return compose(['exec', '-T', 'neo4j', 'sh', '-c', 'export NEO4J_PASSWORD="${NEO4J_AUTH#*/}"; cypher-shell -u neo4j --format plain "' + query + '"']);
}
function token(customerId) {
  const key = fs.readFileSync(path.join(runtime, 'offline-jwt-private.pem'));
  const now = Math.floor(Date.now() / 1000);
  const encode = x => Buffer.from(JSON.stringify(x)).toString('base64url');
  const input = encode({ alg: 'RS256', kid: 'team-demo-offline-only', typ: 'JWT' }) + '.' + encode({ sub: customerId, iss: 'https://identity.stylemind.local', aud: 'stylemind-api', roles: ['CUSTOMER'], iat: now, exp: now + 900, jti: crypto.randomUUID() });
  return input + '.' + crypto.sign('RSA-SHA256', Buffer.from(input), key).toString('base64url');
}
async function verifyOffline() {
  const base = 'http://127.0.0.1:' + state.ports.gateway + '/stylist/api/v1/stylist';
  const fixture = 'http://127.0.0.1:' + state.fixturePort;
  const before = await http(fixture + '/__stats');
  const auth = token(state.customerId);
  if (!state.smoke) {
    const session = await http(base + '/sessions', { method: 'POST', token: auth, body: { title: 'Team bootstrap offline verification' }, expected: 201 });
    const intake = await http(base + '/sessions/' + session.session_id + '/requests', { method: 'POST', token: auth, expected: 201, body: { message: 'Tôi cần bộ đồ đi làm thanh lịch, ngân sách tối đa 800.000 đồng.', occasion: 'work', style_context: 'minimal', max_budget_vnd: 800000, budget_is_hard: true, constraints: [] } });
    assert.equal(intake.request_status, 'RECEIVED'); assert.equal(intake.generation_started, false);
    const generated = await http(base + '/requests/' + intake.request_id + '/generate', { method: 'POST', body: {}, token: auth, timeout: 35000 });
    assert.equal(generated.request_status, 'SUCCEEDED'); assert.equal(generated.recommendations.length, 1);
    state.smoke = { sessionId: session.session_id, requestId: intake.request_id, recommendationId: generated.recommendations[0].recommendation_id }; flush();
    const after = await http(fixture + '/__stats');
    assert.equal(after.queryEmbeddings - before.queryEmbeddings, 1); assert.equal(after.generations - before.generations, 1); assert.equal(after.documentEmbeddings - before.documentEmbeddings, 0);
  }
  const detail = await http(base + '/recommendations/' + state.smoke.recommendationId, { token: auth });
  assert.deepEqual(detail.items.map(i => i.slot_name).sort(), ['BOTTOM', 'TOP']);
  assert(detail.items.every(i => i.current_availability === 'AVAILABLE' && i.current_unit_price_vnd > 0 && i.product_name && i.images.length));
  assert.equal(detail.total_current_price_vnd, detail.items.reduce((n, i) => n + i.current_unit_price_vnd, 0)); assert(detail.total_current_price_vnd <= 800000);
  await http(base + '/requests/' + state.smoke.requestId, { token: token(crypto.randomUUID()), expected: 404 });
  const replayBefore = await http(fixture + '/__stats');
  await http(base + '/requests/' + state.smoke.requestId + '/generate', { method: 'POST', body: {}, token: auth });
  assert.deepEqual(await http(fixture + '/__stats'), replayBefore, 'Replay invoked provider');
  assert.equal((await pg('stylemind_stylist', "SELECT count(*) FROM wardrobe_recommendation WHERE styling_request_id='" + state.smoke.requestId + "'")).trim(), '1');
  return { mode: 'OFFLINE_GEMINI_HTTP_FIXTURE', ...state.smoke, query_embedding: 'FIXTURE_ONLY; stored catalog vectors are REAL', totalVnd: detail.total_current_price_vnd, items: detail.items.map(i => ({ productId: i.product_id, variantId: i.shop_variant_id, name: i.product_name, price: i.current_unit_price_vnd, slot: i.slot_name, availability: i.current_availability })), fixtureStats: await http(fixture + '/__stats'), ownerIsolation: 'PASS', replayNoProviderCall: 'PASS' };
}
async function main() {
  if (args.includes('--stop-apps')) {
    for (const [label, record] of Object.entries(state.apps).reverse()) if (await owns(record)) {
      if (process.platform === 'win32') await run('taskkill', ['/PID', String(record.pid), '/T', '/F']);
      else process.kill(record.pid);
      console.log('Stopped owned demo app ' + label);
    }
    state.apps = {}; flush(); console.log('Docker containers/volumes retained.'); return;
  }
  const { bundle, archive, catalog } = validateBundle();
  // Oracle's Windows javapath shim creates another JVM process and can lose log handles.
  const javaInfo = await run('java', ['-XshowSettings:properties', '-version']);
  const javaHome = javaInfo.match(/^\s*java.home\s*=\s*(.+)$/m)?.[1].trim();
  assert(javaHome, 'Cannot resolve java.home');
  javaCommand = path.join(javaHome, 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
  assert(fs.existsSync(javaCommand), 'Missing actual JVM executable');
  flush();
  const values = { DEMO_POSTGRES_PASSWORD: state.passwords.postgres, DEMO_COMMERCE_PASSWORD: state.passwords.commerce, DEMO_STYLIST_PASSWORD: state.passwords.stylist, DEMO_NEO4J_PASSWORD: state.passwords.neo4j, DEMO_POSTGRES_PORT: state.ports.postgres, DEMO_QDRANT_PORT: state.ports.qdrant, DEMO_NEO4J_PORT: state.ports.neo4j };
  fs.writeFileSync(envFile, Object.entries(values).map(([k, v]) => k + '=' + v).join('\n') + '\n', { mode: 0o600 });
  console.log('Starting dedicated Docker project ' + project);
  await compose(['up', '-d', '--wait', '--wait-timeout', '210']);
  await waitFor('Qdrant', async () => (await http('http://127.0.0.1:' + state.ports.qdrant + '/collections')).status === 'ok');
  const alias = await restoreQdrant(archive); console.log('Qdrant: restored/verified 10 REAL distinct vectors + manifest');
  if (!state.fixturePort) state.fixturePort = await freePort(); if (!state.jwksPort) state.jwksPort = await freePort(); flush();
  const fixtureUrl = 'http://127.0.0.1:' + state.fixturePort;
  const jwksUrl = 'http://127.0.0.1:' + state.jwksPort;
  await startApp('jwks', process.execPath, [path.join(__dirname, 'team-demo-http.cjs'), runtime, String(state.jwksPort)], {}, () => http(jwksUrl + '/healthz'));
  await startApp('fixture', process.execPath, [path.join(__dirname, 'offline-gemini-http-fixture.cjs'), String(state.fixturePort), runtime], {}, () => http(fixtureUrl + '/healthz'));
  const common = { STYLEMIND_JWT_JWK_SET_URI: jwksUrl + '/.well-known/jwks.json', STYLEMIND_JWT_JWS_ALGORITHM: 'RS256', STYLEMIND_JWT_ISSUER: 'https://identity.stylemind.local', STYLEMIND_JWT_AUDIENCE: 'stylemind-api' };
  const commerceUrl = 'http://127.0.0.1:' + state.ports.commerce;
  const commerceJar = await build('commerce-service');
  await startApp('commerce', javaCommand, javaArgs(320, 'commerce', commerceJar), { ...common, SERVER_PORT: state.ports.commerce, STYLEMIND_COMMERCE_DB_URL: 'jdbc:postgresql://127.0.0.1:' + state.ports.postgres + '/stylemind_commerce', STYLEMIND_COMMERCE_DB_USERNAME: 'commerce_demo', STYLEMIND_COMMERCE_DB_PASSWORD: state.passwords.commerce, STYLEMIND_CATALOG_SEED_ENABLED: 'false', STYLEMIND_INVENTORY_EXPIRY_ENABLED: 'false' }, () => http(commerceUrl + '/actuator/health'));
  await run('docker', ['compose', '--env-file', envFile, '-f', composeFile, '-p', project, 'exec', '-T', 'postgres', 'psql', '-U', 'demo_admin', '-d', 'stylemind_commerce', '-v', 'ON_ERROR_STOP=1'], { input: fs.readFileSync(path.join(dataDir, 'commerce-seed.sql'), 'utf8') });
  assert.equal((await pg('stylemind_commerce', 'SELECT count(*) FROM product')).trim(), '10');
  assert.equal((await pg('stylemind_commerce', 'SELECT count(*) FROM product_variant')).trim(), '10');
  const imageChecks = [];
  for (const p of catalog.products) {
    const current = await http(commerceUrl + '/api/v1/products/' + p.productId);
    assert.equal(current.product_status, 'ACTIVE'); assert.equal(current.variants.length, 1); const v = current.variants[0], expected = p.variants[0];
    assert.equal(v.variant_id, expected.variantId); assert.equal(v.unit_price_vnd, expected.unitPriceVnd); assert.equal(v.variant_status, 'ACTIVE'); assert(v.available_quantity > 0); assert.equal(v.color, expected.color); assert.equal(v.images[0].image_url, expected.images[0].imageUrl);
    const response = await fetch(v.images[0].image_url, { signal: AbortSignal.timeout(20000) });
    assert(response.ok && (response.headers.get('content-type') || '').startsWith('image/'), 'Image unavailable: ' + p.productId);
    assert((await response.arrayBuffer()).byteLength > 1000); imageChecks.push({ productId: p.productId, status: response.status });
  }
  console.log('Commerce: 10 distinct ACTIVE products/variants, available, images HTTP 200');
  const stylistJar = await build('stylist-service');
  const localSettings = {
    STYLEMIND_COMMERCE_DB_URL: 'jdbc:postgresql://localhost:' + state.ports.postgres + '/stylemind_commerce', STYLEMIND_COMMERCE_DB_USERNAME: 'commerce_demo', STYLEMIND_COMMERCE_DB_PASSWORD: state.passwords.commerce,
    STYLEMIND_STYLIST_DB_URL: 'jdbc:postgresql://localhost:' + state.ports.postgres + '/stylemind_stylist', STYLEMIND_STYLIST_DB_USERNAME: 'stylist_demo', STYLEMIND_STYLIST_DB_PASSWORD: state.passwords.stylist,
    STYLEMIND_STYLIST_QDRANT_URL: 'http://localhost:' + state.ports.qdrant, STYLEMIND_STYLIST_QDRANT_COLLECTION_ALIAS: alias,
    STYLEMIND_STYLIST_CATALOG_POLICY_FILE: path.join(dataDir, 'catalog-policy.json').replaceAll('\\', '/'),
    STYLEMIND_STYLIST_NEO4J_ENABLED: 'true', STYLEMIND_STYLIST_NEO4J_URI: 'bolt://localhost:' + state.ports.neo4j, STYLEMIND_STYLIST_NEO4J_USERNAME: 'neo4j', STYLEMIND_STYLIST_NEO4J_PASSWORD: state.passwords.neo4j,
    STYLEMIND_STYLIST_KNOWLEDGE_ARTIFACT_ID: 'synthetic-wave2-http-knowledge', STYLEMIND_STYLIST_KNOWLEDGE_ARTIFACT_VERSION: 'fixture-v1', STYLEMIND_STYLIST_TEXT_TEMPLATE_REVISION: 'catalog-v1', STYLEMIND_STYLIST_CATALOG_IMPORT_ENABLED: 'false'
  };
  const stylistUrl = 'http://127.0.0.1:' + state.ports.stylist;
  await startApp('stylist', javaCommand, javaArgs(320, 'stylist', stylistJar), { ...common, ...localSettings, SERVER_PORT: state.ports.stylist, SPRING_PROFILES_ACTIVE: 'knowledge-import,offline-gemini-mock', STYLEMIND_COMMERCE_BASE_URL: commerceUrl, STYLEMIND_AI_PROVIDER: 'gemini', STYLEMIND_GEMINI_LIVE_CALLS_ENABLED: 'false', STYLEMIND_GEMINI_MOCK_ENABLED: 'true', STYLEMIND_GEMINI_MOCK_BASE_URL: fixtureUrl, STYLEMIND_GEMINI_GENERATION_MODEL: 'gemini-3.5-flash-lite', STYLEMIND_STYLIST_KNOWLEDGE_IMPORT_FILE: path.join(dataDir, 'knowledge-artifact.json') }, () => http(stylistUrl + '/actuator/health'));
  await waitFor('Neo4j demo artifact import', async () => /1,\s*1/.test(await neoCount()), 30000);
  console.log('Neo4j: one original demo artifact + one entry; existing importer MERGE');
  const gatewayJar = await build('api-gateway');
  const gatewayUrl = 'http://127.0.0.1:' + state.ports.gateway;
  await startApp('gateway', javaCommand, javaArgs(256, 'gateway', gatewayJar), { SERVER_PORT: state.ports.gateway, STYLEMIND_STYLIST_BASE_URL: stylistUrl, STYLEMIND_COMMERCE_BASE_URL: commerceUrl }, () => http(gatewayUrl + '/actuator/health'));
  const migrationCounts = {};
  for (const service of ['commerce', 'stylist']) { const expected = fs.readdirSync(path.join(root, service + '-service', 'src/main/resources/db/migration')).filter(f => /^V.*\.sql$/.test(f)).length; const actual = Number((await pg('stylemind_' + service, 'SELECT count(*) FROM flyway_schema_history WHERE success')).trim()); assert.equal(actual, expected, service + ' migration count'); migrationCounts[service] = actual; }
  const report = { result: 'DATA_READY', project, testedAt: new Date().toISOString(), modes: { catalogVectors: 'RECORDED_REAL_GEMINI', knowledge: bundle.knowledge_kind, generation: 'OFFLINE_FIXTURE_ONLY' }, commerce: { products: 10, variants: 10, activeAvailable: 10, images: imageChecks }, qdrant: { candidates: 10, distinctVectors: 10, totalPoints: 11, dimensions: 1536, distance: 'Cosine', alias, collection: archive.source_collection }, neo4j: { artifacts: 1, entries: 1, provenance: bundle.knowledge_source }, migrations: migrationCounts, gatewayUrl, localConfigApplied: false };
  if (args.includes('--verify-offline')) { report.offlineGenerate = await verifyOffline(); report.result = 'FRESH_OR_REPEAT_OFFLINE_PASS'; console.log('Offline Generate: SUCCEEDED + detail TOP/BOTTOM + DB persistence + owner isolation + idempotent replay'); }
  if (args.includes('--configure-root-env')) {
    const file = path.join(root, '.env'); let text = fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : '';
    for (const [key, value] of Object.entries(localSettings)) { const pattern = new RegExp('^' + key + '=.*$', 'm'); text = pattern.test(text) ? text.replace(pattern, () => key + '=' + value) : text.replace(/\s*$/, '') + '\n' + key + '=' + value + '\n'; }
    fs.writeFileSync(file, text, { mode: 0o600 }); report.localConfigApplied = true; console.log('Updated data endpoints in root .env; Gemini key/meter/live configuration not edited.');
  }
  save(path.join(runtime, 'readiness.json'), report);
  console.log('READY ' + gatewayUrl + '\nReport: ' + path.join(runtime, 'readiness.json') + '\nDedicated processes and named volumes remain available.');
}
main().catch(e => { console.error('BOOTSTRAP FAIL: ' + redact(e.message)); console.error('Logs: ' + runtime); process.exitCode = 1; });
