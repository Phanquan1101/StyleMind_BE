#!/usr/bin/env node
/*
 * seed-dashboard-demo.cjs — CHI DUNG LOCAL / DEMO. Khong chay tren moi truong that.
 *
 * Tao ~54 don mau rai 30 ngay qua cho an/binh/chi@sm.test de dashboard Tong quan admin co du lieu:
 * hoan tat, dang giao, dang xu ly, cho xac nhan, cho thanh toan, da huy; COD da thu, VNPAY, VietQR da tra;
 * vai khoan hoan tien. Ngay tao don / ngay nhan tien duoc lui ve qua khu, nen script ghi THANG vao DB commerce
 * (qua `docker exec <postgres> psql`) thay vi goi API (API khong cho lui ngay). Khong dung toi ton kho, gio hang,
 * payment-service hay don that.
 *
 *   node scripts/seed-dashboard-demo.cjs            # tao (idempotent: da co don mau thi bo qua)
 *   node scripts/seed-dashboard-demo.cjs --reset    # chi xoa don mau (checkout_key bat dau bang demo-dashboard-)
 *
 * Don mau duoc danh dau bang checkout_key = 'demo-dashboard-NN'; --reset xoa dung nhung don do (kem dong con,
 * su kien thanh toan, hoan tien cua chung). Can: stack dang chay (gateway :8080), tai khoan seed
 * (node scripts/seed-test-data.cjs), mat khau SEED_PASSWORD (mac dinh Stylemind@123).
 * Bien moi truong tuy chon: GATEWAY_URL, POSTGRES_CONTAINER (mac dinh stylemind-postgres-1), SEED_PASSWORD.
 */
const { spawnSync } = require('node:child_process')
const crypto = require('node:crypto')

const GATEWAY = process.env.GATEWAY_URL || 'http://localhost:8080'
const CONTAINER = process.env.POSTGRES_CONTAINER || 'stylemind-postgres-1'
const PASSWORD = process.env.SEED_PASSWORD || 'Stylemind@123'
const MARK = 'demo-dashboard-'
const ORDER_COUNT = 54

function psql(sql) {
  const result = spawnSync('docker', ['exec', '-i', CONTAINER, 'sh', '-c',
    'psql -U "$POSTGRES_USER" -d stylemind_commerce -v ON_ERROR_STOP=1 -At'], { input: sql, encoding: 'utf8' })
  if (result.status !== 0) throw new Error('psql loi: ' + (result.stderr || result.stdout || '').trim().slice(0, 400))
  return result.stdout.trim()
}
const q = value => (value === null || value === undefined ? 'NULL' : "'" + String(value).replace(/'/g, "''") + "'")
const ts = date => q(date.toISOString())

async function api(method, path, { token, body } = {}) {
  const response = await fetch(GATEWAY + path, {
    method,
    headers: { Accept: 'application/json', ...(body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  })
  const text = await response.text()
  let json = null
  try { json = text ? JSON.parse(text) : null } catch { /* not json */ }
  if (!response.ok) throw new Error(`${method} ${path} -> ${response.status} ${text.slice(0, 160)}`)
  return json
}
async function account(email) {
  const login = await api('POST', '/identity/api/v1/auth/login', { body: { email, password: PASSWORD } })
  const me = await api('GET', '/identity/api/v1/me', { token: login.access_token })
  return { email, id: me.account_id, name: email.split('@')[0] }
}

function reset() {
  const where = `checkout_key LIKE '${MARK}%'`
  const out = psql(`
BEGIN;
CREATE TEMP TABLE demo_ids AS SELECT order_id FROM customer_order WHERE ${where};
DELETE FROM refund_result_inbox WHERE order_id IN (SELECT order_id FROM demo_ids);
DELETE FROM payment_event_inbox WHERE order_id IN (SELECT order_id FROM demo_ids);
DELETE FROM order_status_history WHERE order_id IN (SELECT order_id FROM demo_ids);
DELETE FROM order_item WHERE order_id IN (SELECT order_id FROM demo_ids);
DELETE FROM order_address_snapshot WHERE order_id IN (SELECT order_id FROM demo_ids);
DELETE FROM customer_order WHERE order_id IN (SELECT order_id FROM demo_ids);
SELECT count(*) FROM demo_ids;
COMMIT;`)
  const removed = out.split('\n').filter(line => /^\d+$/.test(line)).pop()
  console.log(`Da xoa ${removed} don mau (checkout_key '${MARK}*'). Don that khong bi dong toi.`)
}

function rng(seed) { // mulberry32: cung ket qua moi lan chay
  let a = seed
  return () => { a |= 0; a = (a + 0x6d2b79f5) | 0; let t = Math.imul(a ^ (a >>> 15), 1 | a); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296 }
}

async function seed() {
  const existing = Number(psql(`SELECT count(*) FROM customer_order WHERE checkout_key LIKE '${MARK}%'`))
  if (existing > 0) { console.log(`Da co ${existing} don mau — bo qua (dung --reset de xoa roi chay lai).`); return }

  const customers = await Promise.all(['an@sm.test', 'binh@sm.test', 'chi@sm.test'].map(account))
  const admin = await account('admin@sm.test').catch(() => null)
  const confirmer = (admin || customers[0]).id

  // Bien the that, con ban, gia >= 50.000 d (VNPAY toi thieu 5.000 d), uu tien vai san pham "ban chay".
  const products = (await api('GET', '/commerce/api/v1/products?size=60')).items
  const pool = []
  for (const product of products) {
    if (pool.length >= 14) break
    const detail = await api('GET', '/commerce/api/v1/products/' + product.product_id)
    const variant = (detail.variants || []).find(v => v.variant_status === 'ACTIVE' && v.unit_price_vnd >= 50000 && (v.images || []).length)
    if (variant) pool.push({ variant, product: detail, image: variant.images[0].image_url || variant.images[0].url || null })
  }
  if (pool.length < 6) throw new Error('Khong du san pham mau co anh de tao don (can >= 6).')
  const weights = pool.map((_, index) => (index < 3 ? 5 : index < 7 ? 3 : 1))
  const totalWeight = weights.reduce((a, b) => a + b, 0)

  const random = rng(20261113)
  const pick = list => list[Math.floor(random() * list.length)]
  const pickProduct = () => { let r = random() * totalWeight; for (let i = 0; i < pool.length; i++) { r -= weights[i]; if (r <= 0) return pool[i] } return pool[0] }
  const now = new Date()
  const minutes = n => n * 60000
  const addresses = ['12 Nguyen Hue, Quan 1, TP.HCM', '45 Le Loi, Quan 3, TP.HCM', '8 Tran Phu, Hai Chau, Da Nang', '21 Ly Thuong Kiet, Hoan Kiem, Ha Noi']
  const sql = ['BEGIN;']
  const summary = { COMPLETED: 0, SHIPPED: 0, PROCESSING: 0, CONFIRMED: 0, AWAITING: 0, PENDING_PAYMENT: 0, CANCELLED: 0 }
  let refunds = 0

  for (let index = 0; index < ORDER_COUNT; index++) {
    const customer = customers[index % 3]
    const dayOffset = Math.min(29, Math.floor(Math.pow(random(), 1.35) * 30)) // nhieu don gan day hon
    const placed = new Date(now.getTime() - dayOffset * 86400000)
    placed.setHours(8 + Math.floor(random() * 13), Math.floor(random() * 60), 0, 0)
    if (placed > now) placed.setTime(now.getTime() - minutes(20 + Math.floor(random() * 200)))
    const roll = random()
    const method = roll < 0.4 ? 'VNPAY' : roll < 0.73 ? 'COD' : 'SEPAY_VIETQR'
    const online = method !== 'COD'

    // Trang thai theo tuoi don
    let kind
    const r = random()
    if (dayOffset >= 8) kind = r < 0.78 ? 'COMPLETED' : 'CANCELLED'
    else if (dayOffset >= 3) kind = r < 0.45 ? 'COMPLETED' : r < 0.65 ? 'SHIPPED' : r < 0.75 ? 'PROCESSING' : r < 0.9 ? 'CANCELLED' : 'CONFIRMED'
    else kind = r < 0.3 ? 'AWAITING' : r < 0.5 ? 'CONFIRMED' : r < 0.65 ? 'PROCESSING' : r < 0.8 ? 'SHIPPED' : r < 0.9 ? 'CANCELLED' : (online ? 'PENDING_PAYMENT' : 'AWAITING')
    summary[kind]++

    const lineCount = 1 + Math.floor(random() * 3)
    const lines = []
    const used = new Set()
    for (let l = 0; l < lineCount; l++) {
      const item = pickProduct()
      if (used.has(item.variant.variant_id)) continue
      used.add(item.variant.variant_id)
      lines.push({ item, quantity: 1 + (random() < 0.25 ? 1 : 0) })
    }
    const total = lines.reduce((sum, line) => sum + line.item.variant.unit_price_vnd * line.quantity, 0)

    const orderId = crypto.randomUUID()
    const number = 'SM-' + orderId.replace(/-/g, '').slice(0, 8).toUpperCase()
    const at = offset => new Date(Math.min(now.getTime() - minutes(5), placed.getTime() + offset))
    const paidAt = online ? at(minutes(3 + Math.floor(random() * 55))) : null
    const confirmedAt = at(minutes(20 + Math.floor(random() * 160)) + (online ? minutes(60) : 0))
    const completedAt = at(minutes(24 * 60 * (1 + Math.floor(random() * 3))) + minutes(Math.floor(random() * 300)))
    const confirmed = ['CONFIRMED', 'PROCESSING', 'SHIPPED', 'COMPLETED'].includes(kind)
    const paidOnline = online && (['PROCESSING', 'SHIPPED', 'COMPLETED', 'CONFIRMED', 'AWAITING'].includes(kind) || (kind === 'CANCELLED' && random() < 0.35))
    const codCollected = !online && kind === 'COMPLETED'

    let status
    if (kind === 'CANCELLED') status = 'CANCELLED'
    else if (kind === 'PENDING_PAYMENT') status = 'PENDING_PAYMENT'
    else if (kind === 'COMPLETED' || kind === 'SHIPPED' || kind === 'PROCESSING') status = kind
    else status = online ? 'PAID' : 'PLACED' // AWAITING / CONFIRMED
    const confirmation = confirmed ? 'CONFIRMED' : 'UNCONFIRMED'

    sql.push(`INSERT INTO customer_order (order_id, order_number, customer_id, checkout_key, order_status, payment_method, accepted_total_vnd, placed_at, completed_at, confirmation_state, confirmed_by_user_id, confirmed_at)
VALUES (${q(orderId)}, ${q(number)}, ${q(customer.id)}, ${q(MARK + String(index + 1).padStart(2, '0'))}, ${q(status)}, ${q(method)}, ${total}, ${ts(placed)}, ${status === 'COMPLETED' ? ts(completedAt) : 'NULL'}, ${q(confirmation)}, ${confirmed ? q(confirmer) : 'NULL'}, ${confirmed ? ts(confirmedAt) : 'NULL'});`)
    sql.push(`INSERT INTO order_address_snapshot (order_id, recipient_name, recipient_phone, address_line) VALUES (${q(orderId)}, ${q(customer.name.charAt(0).toUpperCase() + customer.name.slice(1))}, '0912345678', ${q(pick(addresses))});`)
    for (const line of lines) {
      const { variant, product, image } = line.item
      sql.push(`INSERT INTO order_item (order_item_id, order_id, variant_id, sku_snapshot, product_name_snapshot, brand_name_snapshot, color_snapshot, size_snapshot, material_snapshot, image_url_snapshot, quantity, unit_price_vnd)
VALUES (${q(crypto.randomUUID())}, ${q(orderId)}, ${q(variant.variant_id)}, ${q(variant.sku)}, ${q(product.name)}, ${q(product.brand_name || 'StyleMind')}, ${q(variant.color)}, ${q(variant.size)}, ${q(variant.material)}, ${q(image)}, ${line.quantity}, ${variant.unit_price_vnd});`)
    }
    const history = (from, to, when, reason) => sql.push(`INSERT INTO order_status_history (history_id, order_id, from_status, to_status, actor_user_id, changed_at, reason) VALUES (${q(crypto.randomUUID())}, ${q(orderId)}, ${q(from)}, ${q(to)}, ${q(from === null ? customer.id : confirmer)}, ${ts(when)}, ${q(reason)});`)
    history(null, online ? 'PENDING_PAYMENT' : 'PLACED', placed, 'Order created')
    if (online && paidOnline) history('PENDING_PAYMENT', 'PAID', paidAt, 'Payment confirmed')
    if (['PROCESSING', 'SHIPPED', 'COMPLETED'].includes(kind)) history(online ? 'PAID' : 'PLACED', 'PROCESSING', confirmedAt, 'Seed demo')
    if (['SHIPPED', 'COMPLETED'].includes(kind)) history('PROCESSING', 'SHIPPED', new Date(Math.min(now.getTime() - minutes(5), confirmedAt.getTime() + minutes(180))), 'Seed demo')
    if (kind === 'COMPLETED') history('SHIPPED', 'COMPLETED', completedAt, 'Seed demo')
    if (kind === 'CANCELLED') history(online && paidOnline ? 'PAID' : online ? 'PENDING_PAYMENT' : 'PLACED', 'CANCELLED', at(minutes(90)), 'Seed demo')

    const eventAt = codCollected ? completedAt : paidOnline ? paidAt : null
    if (eventAt) {
      const reference = 'SM' + crypto.randomBytes(6).toString('hex').toUpperCase()
      sql.push(`INSERT INTO payment_event_inbox (event_id, event_fingerprint, order_id, payment_attempt_id, customer_id, payment_method, payment_reference, amount_vnd, confirmed_at, received_at, outcome, reason)
VALUES (${q(crypto.randomUUID())}, ${q(crypto.randomBytes(32).toString('hex'))}, ${q(orderId)}, ${q(crypto.randomUUID())}, ${q(customer.id)}, ${q(method)}, ${q(reference)}, ${total}, ${ts(eventAt)}, ${ts(eventAt)}, 'APPLIED', ${q(codCollected ? 'COD_COLLECTION_CONFIRMED' : 'PAYMENT_CONFIRMED')});`)
      // Vai khoan hoan tien mot phan cho don online da hoan tat
      if (kind === 'COMPLETED' && online && refunds < 3 && index % 7 === 3) {
        const refundAt = new Date(Math.min(now.getTime() - minutes(5), completedAt.getTime() + minutes(24 * 60)))
        sql.push(`INSERT INTO refund_result_inbox (refund_id, source_type, source_id, order_id, amount_vnd, refunded_at) VALUES (${q(crypto.randomUUID())}, 'RETURN', ${q(crypto.randomUUID())}, ${q(orderId)}, ${Math.round(total * 0.3 / 1000) * 1000 || 1000}, ${ts(refundAt)});`)
        refunds++
      }
    }
  }
  sql.push('COMMIT;')
  psql(sql.join('\n'))
  const count = Number(psql(`SELECT count(*) FROM customer_order WHERE checkout_key LIKE '${MARK}%'`))
  console.log(`Da tao ${count} don mau (checkout_key '${MARK}*'), ${refunds} khoan hoan tien.`)
  console.log('Phan bo:', Object.entries(summary).map(([k, v]) => `${k}=${v}`).join(', '))
  console.log('Xoa lai bang: node scripts/seed-dashboard-demo.cjs --reset')
}

;(process.argv.includes('--reset') ? Promise.resolve().then(reset) : seed()).catch(error => { console.error('LOI:', error.message); process.exit(1) })
