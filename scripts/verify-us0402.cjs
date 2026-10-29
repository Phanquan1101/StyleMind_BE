// Disposable two-JAR verification. Never reads .env or uses the owner's Compose databases.
const fs = require('node:fs');
const path = require('node:path');
const cp = require('node:child_process');
const http = require('node:http');
const net = require('node:net');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const output = path.join(root, 'target/us0402-e2e');
fs.mkdirSync(output, { recursive: true });
const name = `stylemind-us0402-${process.pid}-${Date.now()}`;
const label = 'us0402-e2e';
const children = [];
const logs = [];
let created = false;
let bridge;
let lost = false;
const deliveries = new Map();
const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
function docker(args) { return cp.execFileSync('docker', args, { encoding: 'utf8', windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] }).trim(); }
function sql(database, statement) { return docker(['exec', name, 'psql', '-X', '-v', 'ON_ERROR_STOP=1', '-U', 'review_test', '-d', database, '-Atc', statement]); }
async function port() {
  const server = net.createServer(); await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const number = server.address().port; await new Promise(resolve => server.close(resolve)); return number;
}
async function until(probe, message, timeout = 90000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    try { if (await probe()) return; } catch (_) { }
    await wait(400);
  }
  throw new Error(message);
}
function launch(service, number, dbPort, extras) {
  const jar = path.join(root, service, 'target', `${service}-0.0.1-SNAPSHOT.jar`);
  assert.ok(fs.existsSync(jar), `Run ${service} Maven verify first`);
  const database = service === 'commerce-service' ? 'commerce_e2e' : 'payment_e2e';
  const owner = service === 'commerce-service' ? 'commerce_e2e_app' : 'payment_e2e_app';
  const log = fs.openSync(path.join(output, `${service}.log`), 'w'); logs.push(log);
  const args = ['-Duser.timezone=UTC', '-jar', jar, '--server.address=127.0.0.1', `--server.port=${number}`,
    `--spring.datasource.url=jdbc:postgresql://127.0.0.1:${dbPort}/${database}`,
    `--spring.datasource.username=${owner}`, '--spring.datasource.password=e2e-test-only',
    '--stylemind.security.jwt.jwk-set-uri=', '--stylemind.security.jwt.jws-algorithm=', ...extras];
  const child = cp.spawn('java', args, { cwd: root, windowsHide: true, stdio: ['ignore', log, log] });
  child.on('error', () => {}); children.push(child); return child;
}
async function post(number, endpoint, body, header, key) {
  const response = await fetch(`http://127.0.0.1:${number}${endpoint}`, { method: 'POST',
    headers: { 'Content-Type': 'application/json', [header]: key }, body: JSON.stringify(body), signal: AbortSignal.timeout(10000) });
  const text = await response.text(); return { status: response.status, body: text ? JSON.parse(text) : null };
}
async function main() {
  try {
    docker(['run', '-d', '--name', name, '--label', `stylemind.validation=${label}`, '-e', 'POSTGRES_USER=review_test',
      '-e', 'POSTGRES_PASSWORD=e2e-test-only', '-e', 'POSTGRES_DB=commerce_e2e', '-p', '127.0.0.1::5432', 'postgres:18.6']);
    created = true;
    await until(() => docker(['exec', name, 'pg_isready', '-U', 'review_test', '-d', 'commerce_e2e']).includes('accepting'), 'Isolated PostgreSQL did not start');
    const dbPort = Number(docker(['port', name, '5432/tcp']).split(':').pop());
    sql('commerce_e2e', "CREATE ROLE commerce_e2e_app LOGIN PASSWORD 'e2e-test-only'; CREATE ROLE payment_e2e_app LOGIN PASSWORD 'e2e-test-only';");
    sql('commerce_e2e', 'ALTER DATABASE commerce_e2e OWNER TO commerce_e2e_app; GRANT ALL ON SCHEMA public TO commerce_e2e_app;');
    sql('commerce_e2e', 'CREATE DATABASE payment_e2e OWNER payment_e2e_app');
    const commercePort = await port(), paymentPort = await port();
    bridge = http.createServer(async (request, response) => {
      try {
        const chunks = []; for await (const chunk of request) chunks.push(chunk);
        const bytes = Buffer.concat(chunks), event = JSON.parse(bytes.toString());
        deliveries.set(event.event_id, (deliveries.get(event.event_id) || 0) + 1);
        const target = await fetch(`http://127.0.0.1:${commercePort}/internal/v1/payment-events`, { method: 'POST',
          headers: { 'Content-Type': 'application/json', 'X-StyleMind-Service-Token': request.headers['x-stylemind-service-token'] || '' },
          body: bytes, signal: AbortSignal.timeout(10000) });
        const result = await target.text();
        if (!lost && target.status === 200 && JSON.parse(result).outcome === 'APPLIED') {
          lost = true; request.socket.destroy(); return; // Real receipt committed, sender receives no acknowledgement.
        }
        response.writeHead(target.status, { 'Content-Type': 'application/json' }); response.end(result);
      } catch (_) { response.writeHead(503); response.end('{}'); }
    });
    await new Promise(resolve => bridge.listen(0, '127.0.0.1', resolve));
    launch('commerce-service', commercePort, dbPort, ['--stylemind.payment.ingress-token=e2e-events-only']);
    await until(async () => (await fetch(`http://127.0.0.1:${commercePort}/actuator/health`, { signal: AbortSignal.timeout(1500) })).status === 200,
      'Commerce JAR did not become healthy');
    launch('payment-service', paymentPort, dbPort, ['--stylemind.payment.handoff-token=e2e-handoff-only',
      '--stylemind.payment.sepay.webhook-auth-mode=api-key', '--stylemind.payment.sepay.webhook-api-key=e2e-webhook-only',
      '--stylemind.payment.sepay.qr-bank-name=TESTBANK', '--stylemind.payment.sepay.qr-account-number=000-test-only',
      `--stylemind.payment.commerce.base-url=http://127.0.0.1:${bridge.address().port}`,
      '--stylemind.payment.commerce.service-token=e2e-events-only', '--stylemind.payment.events.delivery-enabled=true',
      '--stylemind.payment.events.poll-delay-ms=500']);
    await until(async () => (await fetch(`http://127.0.0.1:${paymentPort}/actuator/health`, { signal: AbortSignal.timeout(1500) })).status === 200,
      'Payment JAR did not become healthy');
    let event = 900000;
    async function settle(state) {
      const order = crypto.randomUUID(), owner = crypto.randomUUID();
      sql('commerce_e2e', `INSERT INTO public.customer_order(order_id,order_number,customer_id,checkout_key,order_status,payment_method,accepted_total_vnd,placed_at)
        VALUES ('${order}','E2E-${order}','${owner}','${order}','${state}','SEPAY_VIETQR',125000,now())`);
      const attempt = await post(paymentPort, '/internal/v1/payments/order-attempts', { order_id: order, customer_id: owner,
        payment_method: 'SEPAY_VIETQR', accepted_total_vnd: 125000, order_status: 'PENDING_PAYMENT', request_key: `initial-${order}` },
        'X-StyleMind-Service-Token', 'e2e-handoff-only');
      assert.equal(attempt.status, 201);
      const webhook = { id: ++event, code: attempt.body.reference, transferType: 'in', transferAmount: 125000 };
      assert.equal((await post(paymentPort, '/internal/v1/sepay/webhooks', webhook, 'Authorization', 'Apikey e2e-webhook-only')).status, 200);
      await until(() => sql('payment_e2e', `SELECT count(*) FROM public.payment_outbox WHERE aggregate_id='${attempt.body.id}' AND published_at IS NOT NULL`) === '1',
        'Payment event was not durably acknowledged', 30000);
      return { order, attempt: attempt.body.id, webhook };
    }
    const paid = await settle('PENDING_PAYMENT');
    assert.equal(sql('commerce_e2e', `SELECT order_status FROM public.customer_order WHERE order_id='${paid.order}'`), 'PAID');
    assert.equal(sql('commerce_e2e', `SELECT count(*) FROM public.order_status_history WHERE order_id='${paid.order}'`), '1');
    assert.equal(sql('commerce_e2e', `SELECT count(*) FROM public.payment_event_inbox WHERE order_id='${paid.order}'`), '1');
    assert.ok(lost); assert.ok([...deliveries.values()].some(count => count >= 2));
    assert.equal((await post(paymentPort, '/internal/v1/sepay/webhooks', paid.webhook, 'Authorization', 'Apikey e2e-webhook-only')).status, 200);
    assert.equal(sql('payment_e2e', `SELECT count(*) FROM public.financial_transaction WHERE payment_attempt_id='${paid.attempt}'`), '1');
    const cancelled = await settle('CANCELLED');
    assert.equal(sql('commerce_e2e', `SELECT order_status FROM public.customer_order WHERE order_id='${cancelled.order}'`), 'CANCELLED');
    assert.equal(sql('commerce_e2e', `SELECT outcome FROM public.payment_event_inbox WHERE order_id='${cancelled.order}'`), 'RECONCILIATION_REQUIRED');
    assert.equal(sql('payment_e2e', `SELECT exception_reason FROM public.payment_exception WHERE payment_attempt_id='${cancelled.attempt}'`), 'ORDER_PAYMENT_REJECTED');
    assert.equal(sql('payment_e2e', `SELECT attempt_status FROM public.payment_attempt WHERE payment_attempt_id='${cancelled.attempt}'`), 'SETTLED');
    const result = { result: 'PASS', scenarios: ['SePay settlement and PAID transition', 'Lost ACK replay without duplicate effects',
      'Duplicate webhook without double credit', 'Cancelled Order reconciliation visible in Payment exceptions'], persistentDatabaseTouched: false };
    fs.writeFileSync(path.join(output, 'result.json'), JSON.stringify(result, null, 2)); console.log(JSON.stringify(result));
  } finally {
    for (const child of children) if (child.exitCode === null) child.kill();
    await wait(500);
    for (const fd of logs) { try { fs.closeSync(fd); } catch (_) {} }
    if (bridge) { bridge.closeAllConnections(); await new Promise(resolve => bridge.close(resolve)); }
    if (created && docker(['inspect', '--format', '{{ index .Config.Labels "stylemind.validation" }}', name]) === label)
      docker(['rm', '-f', name]);
  }
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
