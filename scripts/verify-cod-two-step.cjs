// Disposable two-JAR verification. Never reads .env or uses the owner's Compose databases.
const fs = require('node:fs');
const path = require('node:path');
const cp = require('node:child_process');
const http = require('node:http');
const net = require('node:net');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const output = path.join(root, 'target/cod-two-step-e2e');
fs.mkdirSync(output, { recursive: true });
const name = `stylemind-cod-two-step-${process.pid}-${Date.now()}`;
const label = 'cod-two-step-e2e';
const children = [];
const logs = [];
let created = false;
let bridge;
let fixture;
let fixtureUrl;
let livePaymentPort;
let handoffAvailable = true;
let codBlocked = true;
let codLostAck = false;
const ownerId = crypto.randomUUID(), adminId = crypto.randomUUID(), addressId = crypto.randomUUID();
const signing = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
const publicJwk = { ...signing.publicKey.export({ format: 'jwk' }), kid: 'cod-e2e-only', alg: 'RS256', use: 'sig' };
function token(subject, role) {
  const header = Buffer.from(JSON.stringify({ alg: 'RS256', typ: 'JWT', kid: 'cod-e2e-only' })).toString('base64url');
  const body = Buffer.from(JSON.stringify({ sub: subject, roles: [role], iat: Math.floor(Date.now()/1000)-2,
    exp: Math.floor(Date.now()/1000)+600, jti: crypto.randomUUID() })).toString('base64url');
  return header + '.' + body + '.' + crypto.sign('RSA-SHA256', Buffer.from(header+'.'+body), signing.privateKey).toString('base64url');
}
const ownerToken = token(ownerId, 'CUSTOMER'), adminToken = token(adminId, 'ADMIN');
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
    '--stylemind.security.jwt.jwk-set-uri=' + fixtureUrl + '/jwks', '--stylemind.security.jwt.jws-algorithm=RS256', ...extras];
  const child = cp.spawn('java', args, { cwd: root, windowsHide: true, stdio: ['ignore', log, log] });
  child.on('error', () => {}); children.push(child); return child;
}
async function post(number, endpoint, body, header, key) {
  const response = await fetch(`http://127.0.0.1:${number}${endpoint}`, { method: 'POST',
    headers: { 'Content-Type': 'application/json', [header]: key }, body: body === null ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(10000) });
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
        if (event.payment_method === 'COD' && codBlocked) { response.writeHead(503); response.end('{}'); return; }
        const target = await fetch(`http://127.0.0.1:${commercePort}/internal/v1/payment-events`, { method: 'POST',
          headers: { 'Content-Type': 'application/json', 'X-StyleMind-Service-Token': request.headers['x-stylemind-service-token'] || '' },
          body: bytes, signal: AbortSignal.timeout(10000) });
        const result = await target.text();
        if (event.payment_method === 'COD' && !codLostAck && target.status === 200 && JSON.parse(result).outcome === 'APPLIED') {
          codLostAck = true; request.socket.destroy(); return;
        }
        if (event.payment_method === 'SEPAY_VIETQR' && !lost && target.status === 200 && JSON.parse(result).outcome === 'APPLIED') {
          lost = true; request.socket.destroy(); return; // Real receipt committed, sender receives no acknowledgement.
        }
        response.writeHead(target.status, { 'Content-Type': 'application/json' }); response.end(result);
      } catch (_) { response.writeHead(503); response.end('{}'); }
    });
    await new Promise(resolve => bridge.listen(0, '127.0.0.1', resolve));
    fixture = http.createServer(async (request, response) => {
      if (request.url === '/jwks') { response.writeHead(200, {'Content-Type':'application/json'}); response.end(JSON.stringify({keys:[publicJwk]})); return; }
      if (request.url === '/api/v1/me/addresses/' + addressId && request.headers.authorization === 'Bearer ' + ownerToken) {
        response.writeHead(200, {'Content-Type':'application/json'});
        response.end(JSON.stringify({recipient_name:'Test Customer',recipient_phone:'+84900000000',address_line:'Disposable test address'})); return;
      }
      if (request.url === '/internal/v1/payments/order-attempts') {
        if (!handoffAvailable) { response.writeHead(503, {'Content-Type':'application/json'}); response.end('{}'); return; }
        try {
          const chunks=[]; for await (const chunk of request) chunks.push(chunk);
          const upstream = await fetch('http://127.0.0.1:' + livePaymentPort + request.url, {method:'POST',
            headers:{'Content-Type':'application/json','X-StyleMind-Service-Token':request.headers['x-stylemind-service-token']||''},
            body:Buffer.concat(chunks),signal:AbortSignal.timeout(10000)});
          response.writeHead(upstream.status, {'Content-Type':'application/json'}); response.end(await upstream.text()); return;
        } catch (_) { response.writeHead(503); response.end('{}'); return; }
      }
      response.writeHead(404); response.end('{}');
    });
    await new Promise(resolve => fixture.listen(0,'127.0.0.1',resolve));
    fixtureUrl = 'http://127.0.0.1:' + fixture.address().port;
    livePaymentPort = paymentPort;
    launch('commerce-service', commercePort, dbPort, ['--stylemind.payment.ingress-token=e2e-events-only',
      '--stylemind.payment.base-url=' + fixtureUrl, '--stylemind.payment.handoff-token=e2e-handoff-only', '--stylemind.identity.base-url=' + fixtureUrl]);
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
    const category=crypto.randomUUID(), brand=crypto.randomUUID(), product=crypto.randomUUID(), variant=crypto.randomUUID();
    sql('commerce_e2e', "INSERT INTO public.category(category_id,name,slug,is_active) VALUES ('"+category+"','Test','test',true);"+
      "INSERT INTO public.brand(brand_id,name,slug,is_active) VALUES ('"+brand+"','Test','test',true);"+
      "INSERT INTO public.product(product_id,category_id,brand_id,name,product_status) VALUES ('"+product+"','"+category+"','"+brand+"','Test Shirt','ACTIVE');"+
      "INSERT INTO public.product_variant(variant_id,product_id,sku,unit_price_vnd,variant_status) VALUES ('"+variant+"','"+product+"','COD-E2E',125000,'ACTIVE');");
    async function checkoutCod(key) {
      assert.equal((await post(commercePort,'/api/v1/cart/items',{variant_id:variant,quantity:1,guest_session_key:null},'Authorization','Bearer '+ownerToken)).status,200);
      return post(commercePort,'/api/v1/orders',{checkout_key:key,shipping_address_id:addressId,payment_method:'COD'},'Authorization','Bearer '+ownerToken);
    }
    const codOrder=await checkoutCod('cod-e2e-success'); assert.equal(codOrder.status,201); assert.equal(codOrder.body.order_status,'PLACED');
    const codId=codOrder.body.order_id;
    const pending=await post(commercePort,'/api/v1/orders/'+codId+'/payment-attempt',null,'Authorization','Bearer '+ownerToken);
    assert.equal(pending.status,200); assert.equal(pending.body.status,'PENDING');
    const codAttempt=pending.body.id;
    assert.equal(sql('payment_e2e',"SELECT count(*) FROM public.financial_transaction WHERE payment_attempt_id='"+codAttempt+"'"),'0');
    const collectedBody={payment_attempt_id:codAttempt,collected_amount_vnd:125000,collection_reference:'COD-E2E-RECEIPT'};
    assert.equal((await post(paymentPort,'/api/v1/admin/cod-collections',collectedBody,'Authorization','Bearer '+ownerToken)).status,403);
    assert.equal((await post(paymentPort,'/api/v1/admin/cod-collections',{...collectedBody,collected_amount_vnd:1},'Authorization','Bearer '+adminToken)).status,409);
    assert.equal(sql('payment_e2e',"SELECT count(*) FROM public.financial_transaction WHERE payment_attempt_id='"+codAttempt+"'"),'0');
    const collection=await post(paymentPort,'/api/v1/admin/cod-collections',collectedBody,'Authorization','Bearer '+adminToken);
    assert.equal(collection.status,200); assert.equal(collection.body.status,'PAID');
    assert.equal((await post(paymentPort,'/api/v1/admin/cod-collections',collectedBody,'Authorization','Bearer '+adminToken)).status,409);
    assert.equal(sql('payment_e2e',"SELECT count(*) FROM public.financial_transaction WHERE payment_attempt_id='"+codAttempt+"'"),'1');
    assert.equal(sql('payment_e2e',"SELECT recorded_by_user_id::text FROM public.financial_transaction WHERE payment_attempt_id='"+codAttempt+"'"),adminId);
    assert.equal(sql('commerce_e2e',"SELECT order_status FROM public.customer_order WHERE order_id='"+codId+"'"),'PLACED');
    assert.equal(sql('commerce_e2e',"SELECT count(*) FROM public.order_status_history WHERE order_id='"+codId+"' AND to_status='PAID'"),'0');
    const codEventId=sql('payment_e2e',"SELECT outbox_event_id::text FROM public.payment_outbox WHERE aggregate_id='"+codAttempt+"'");
    await until(()=>deliveries.has(codEventId),'COD delivery was not attempted while receiver was unavailable',30000);
    assert.equal(sql('payment_e2e',"SELECT count(*) FROM public.payment_outbox WHERE aggregate_id='"+codAttempt+"' AND published_at IS NULL"),'1');
    assert.equal(sql('commerce_e2e',"SELECT count(*) FROM public.payment_event_inbox WHERE order_id='"+codId+"'"),'0');
    codBlocked=false;
    await until(()=>sql('payment_e2e',"SELECT count(*) FROM public.payment_outbox WHERE aggregate_id='"+codAttempt+"' AND published_at IS NOT NULL")==='1','COD collection was not acknowledged after retry',30000);
    assert(codLostAck); assert(deliveries.get(codEventId)>=3);
    assert.equal(sql('commerce_e2e',"SELECT count(*) FROM public.payment_event_inbox WHERE order_id='"+codId+"' AND payment_method='COD' AND outcome='APPLIED'"),'1');
    assert.equal(sql('commerce_e2e',"SELECT order_status FROM public.customer_order WHERE order_id='"+codId+"'"),'PLACED');
    assert.equal(sql('commerce_e2e',"SELECT count(*) FROM public.order_status_history WHERE order_id='"+codId+"'"),'2');
    assert.equal(sql('payment_e2e',"SELECT count(*) FROM public.financial_transaction WHERE payment_attempt_id='"+codAttempt+"'"),'1');
    const cancelledCod=await checkoutCod('cod-e2e-cancelled'); assert.equal(cancelledCod.status,201);
    const cancelledCodId=cancelledCod.body.order_id;
    const cancelledAttempt=sql('payment_e2e',"SELECT payment_attempt_id::text FROM public.payment_attempt WHERE order_id='"+cancelledCodId+"'");
    sql('commerce_e2e',"UPDATE public.customer_order SET order_status='CANCELLED' WHERE order_id='"+cancelledCodId+"'");
    assert.equal((await post(paymentPort,'/api/v1/admin/cod-collections',{payment_attempt_id:cancelledAttempt,collected_amount_vnd:125000},'Authorization','Bearer '+adminToken)).status,200);
    await until(()=>sql('payment_e2e',"SELECT count(*) FROM public.payment_outbox WHERE aggregate_id='"+cancelledAttempt+"' AND published_at IS NOT NULL")==='1','Cancelled COD collection was not reconciled',30000);
    assert.equal(sql('commerce_e2e',"SELECT order_status FROM public.customer_order WHERE order_id='"+cancelledCodId+"'"),'CANCELLED');
    assert.equal(sql('commerce_e2e',"SELECT outcome FROM public.payment_event_inbox WHERE order_id='"+cancelledCodId+"'"),'RECONCILIATION_REQUIRED');
    assert.equal(sql('payment_e2e',"SELECT exception_reason FROM public.payment_exception WHERE payment_attempt_id='"+cancelledAttempt+"'"),'ORDER_PAYMENT_REJECTED');
    const exceptionList=await fetch('http://127.0.0.1:'+paymentPort+'/api/v1/admin/payment-exceptions',{headers:{Authorization:'Bearer '+adminToken}});
    assert.equal(exceptionList.status,200); assert((await exceptionList.text()).includes(cancelledAttempt));
    assert.equal(sql('payment_e2e',"SELECT attempt_status FROM public.payment_attempt WHERE payment_attempt_id='"+cancelledAttempt+"'"),'SETTLED');
    handoffAvailable=false;
    const delayed=await checkoutCod('cod-e2e-delayed'); assert.equal(delayed.status,201); assert.equal(delayed.body.order_status,'PENDING_PAYMENT');
    const delayedId=delayed.body.order_id;
    assert.equal(sql('payment_e2e',"SELECT count(*) FROM public.payment_attempt WHERE order_id='"+delayedId+"'"),'0');
    handoffAvailable=true;
    const recovered=await post(commercePort,'/api/v1/orders/'+delayedId+'/payment-attempt',null,'Authorization','Bearer '+ownerToken);
    assert.equal(recovered.status,200); assert.equal(recovered.body.status,'PENDING');
    assert.equal(sql('commerce_e2e',"SELECT order_status FROM public.customer_order WHERE order_id='"+delayedId+"'"),'PLACED');
    assert.equal(sql('commerce_e2e',"SELECT count(*) FROM public.order_status_history WHERE order_id='"+delayedId+"'"),'2');
    assert.equal(sql('payment_e2e',"SELECT count(*) FROM public.financial_transaction WHERE payment_attempt_id='"+recovered.body.id+"'"),'0');
    const result = { result: 'PASS', scenarios: ['SePay settlement and PAID transition', 'Lost ACK replay without duplicate effects',
      'Duplicate webhook without double credit', 'Cancelled Order reconciliation visible in Payment exceptions',
      'Real COD checkout auto-confirms unpaid obligation', 'Customer denied and Admin collection records money once', 'COD handoff failure stays pending and owner recovery confirms once',
      'Wrong COD amount records no money', 'Committed COD outbox retries receiver outage without Notification',
      'COD lost ACK replay persists one receipt without changing fulfilment', 'Cancelled COD reconciles to Admin exception while collected money stays settled'], persistentDatabaseTouched: false };
    fs.writeFileSync(path.join(output, 'result.json'), JSON.stringify(result, null, 2)); console.log(JSON.stringify(result));
  } finally {
    for (const child of children) if (child.exitCode === null) child.kill();
    await wait(500);
    for (const fd of logs) { try { fs.closeSync(fd); } catch (_) {} }
    if (bridge) { bridge.closeAllConnections(); await new Promise(resolve => bridge.close(resolve)); }
    if (fixture) { fixture.closeAllConnections(); await new Promise(resolve => fixture.close(resolve)); }
    if (created && docker(['inspect', '--format', '{{ index .Config.Labels "stylemind.validation" }}', name]) === label)
      docker(['rm', '-f', name]);
  }
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
