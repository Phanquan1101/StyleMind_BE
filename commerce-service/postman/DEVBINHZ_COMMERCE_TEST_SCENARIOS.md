# Commerce service — test scenarios for the work of `devbinhz`

## 1. Scope and test setup

This plan was derived from the commits authored by `devbinhz`: category/brand CRUD, product/variant/image CRUD, cart, checkout/order/cancellation, soft deletes, catalog seed, and global variant inventory. The relevant commits are `bd664e3`, `9ae234f`, `96165f8`, `2be0f4d`, `7f55d8d`, and `edfe9b4` (plus the interface/JPA refactors).

Do **not** count the following as `devbinhz` feature ownership when reporting results: guest order/guest payment (`52622ea`), selected checkout/buy-now (`634c09c`), internal variant lookup (`37d46b6`), and payment confirmation/COD collection callbacks (`a4b8053`, `9edda7b`). They can be run as regression tests because they touch the same order module.

Base URL is `http://localhost:8082`, or `http://localhost:8080/commerce` through Gateway. Prepare:

- `ADMIN`: valid JWT containing `ROLE_ADMIN`.
- `CUSTOMER_A`, `CUSTOMER_B`: two valid customer JWTs, each with an Identity address. `CUSTOMER_A` owns `addressA`.
- `guestKey`: 16–128 characters matching `[A-Za-z0-9._:-]`.
- `categoryId`, `brandId`, `productId`, `variantId`, `cartItemId`, `orderId`, `cancelRequestId`: values returned by prior steps.
- Active variant `V1` with price 100,000 VND and quantity 10; inactive variant `V2`; a second active variant `V3` with quantity 1.

For every `4xx`, also assert the standard problem response's `code`; for every state-changing test, re-read the resource and verify persistence. Use a fresh unique slug, SKU, checkout key and guest key per run.

## 2. Shared access and input rules

| ID | Scenario / request | Expected result and rule |
|---|---|---|
| SEC-01 | Call a public catalog/product endpoint without JWT. | `200`; category, brand, product list/detail and public variant batch lookup are public. |
| SEC-02 | Call any `/api/v1/admin/**` with no JWT, then with `CUSTOMER_A`. | `401`, then `403`; only `ROLE_ADMIN` can manage catalog, product, or admin orders. |
| SEC-04 | Send malformed UUID path/query ID, an unknown JSON field, missing required field, wrong JSON type, duplicated query parameter, or a control character in text. | `400 INVALID_REQUEST`; validators use exact request schemas and reject malformed input. |
| SEC-05 | Use a valid but non-existent resource ID. | `404 RESOURCE_NOT_FOUND`; resources are not silently created. |

## 3. Category and brand

Request body (the three fields are mandatory):
```json
{"name":"Men tops","slug":"men-tops","is_active":true}
```

| ID | Scenario / request | Expected result and business rule |
|---|---|---|
| CAT-01 | `POST /api/v1/admin/categories` as ADMIN, then `GET /api/v1/categories/{id}`. Repeat for `/brands`. | `201` with `Location`; public detail is `200`. Name is trimmed, 1–100 Unicode code points; slug is lower-cased. |
| CAT-02 | Create duplicate category slug; create duplicate brand slug. | `409 DUPLICATE_SLUG`; the slug is unique per reference type and remains reserved even after soft delete. |
| CAT-03 | Create with slug `-x`, `x--y`, >64 chars, uppercase input (verify normalization), blank/control-character/>100 name. | Invalid forms `400`; valid uppercase slug is persisted lower-case. Slug pattern: lower-case letters/digits separated by a single hyphen. |
| CAT-04 | `GET /categories`, `/brands` after creating active and inactive rows. | `200`; only active rows, sorted by `name`, then ID. |
| CAT-05 | `GET /api/v1/admin/categories`/`brands` and corresponding `{id}` as ADMIN. | `200`; both active and inactive rows are visible. |
| CAT-06 | `PUT` an inactive row with `is_active:true`; then public GET. | `200`, then `200`; PUT fully replaces and can reactivate the row. |
| CAT-07 | `DELETE` active category/brand twice; read as public and admin. | Both deletes `204`; public GET/list hides it (`404` for detail); admin still sees it with `is_active:false`. Delete is idempotent only for an existing ID. |

## 4. Product, variant, image, and inventory master data

Valid product body:
```json
{"category_id":"<categoryId>","brand_id":"<brandId>","name":"Oxford Shirt","description":"Cotton","demographic_segment":"MEN","product_status":"ACTIVE"}
```
Valid variant body:
```json
{"sku":"OXFORD-BLUE-M","size":"M","color":"Blue","material":"Cotton","unit_price_vnd":100000,"quantity":10,"variant_status":"ACTIVE"}
```

| ID | Scenario / request | Expected result and business rule |
|---|---|---|
| PRD-01 | Create category/brand, then `POST /admin/products` as ADMIN. | `201`; returned product has no variants initially. Category and brand must exist and be active. |
| PRD-02 | Create product with missing/inactive/non-existent category or brand. | `409 CATEGORY_NOT_ACTIVE` or `409 BRAND_NOT_ACTIVE`; product cannot point to an inactive reference. |
| PRD-03 | `PUT /admin/products/{id}` with changed data; retrieve as admin/public. | `200`; it is a full replacement. Public sees it only if product is `ACTIVE` and has an active variant. |
| PRD-04 | `POST /admin/products/{id}/variants` using the valid body. | `201`; `available_quantity` equals supplied `quantity` (10). SKU is normalized to uppercase. |
| PRD-05 | Create/update a variant with same SKU, regardless of product. | `409 DUPLICATE_SKU`; SKU is globally unique and remains reserved after soft delete. |
| PRD-06 | Variant boundary values: price 0 and 1,000,000,000; quantity 0; then price -1/over max/non-integer and quantity -1/non-integer. | Boundary values succeed; invalid values return `400`. Quantity must be non-negative, price is an integer within `[0, 1,000,000,000]`. |
| PRD-07 | Invalid SKU (`"a"`, spaces, >64 chars), invalid status, strings >64 for size/color/material. | `400`; SKU pattern is `[A-Za-z0-9][A-Za-z0-9_-]{0,63}`. |
| PRD-08 | `GET /products` with keyword, category, demographic, min/max price, pages; include active/inactive products and variants. | `200`; only active products with an active variant are listed. Filters apply; sort is name then ID; defaults `page=0,size=20`, maximum size 100. |
| PRD-09 | `GET /products/{id}` for active product with active variant, inactive product, or product with all variants inactive. | `200` only for the first; otherwise `404`. Admin product detail still returns all variants/statuses. |
| PRD-10 | List query invalidity: unsupported key, `min>max`, negative price/page, size 0/101, malformed category UUID, repeated value. | `400 INVALID_REQUEST`. |
| PRD-11 | `POST /variants/batch-lookup` with 1–100 unique IDs including active/inactive/non-existent IDs. | `200`; response contains only active variants belonging to active products, with current quantity/price. Empty, >100 or duplicate IDs returns `400`. |
| PRD-12 | Soft-delete a product twice; then public/admin reads and batch lookup. | `204` twice; public detail/list and lookup exclude it; admin detail retains it as `INACTIVE`. |
| PRD-13 | Soft-delete a variant twice; then public product and admin detail. | `204` twice; public data excludes it. Admin retains the ID/SKU with `INACTIVE`. |
| IMG-01 | `POST /admin/variants/{id}/images` multipart JPEG/PNG/WebP <=5 MiB, twice. | `201`; URL is HTTPS Cloudinary URL. First image is primary and display order 0; next has order 1. |
| IMG-02 | Upload empty, >5 MiB, GIF/PDF, missing `file`, malformed/too-long `alt_text`, or unknown variant. | Invalid file/text: `400`; unknown variant: `404`. |
| IMG-03 | Reorder all image IDs with unique non-negative orders and set primary ID. | `200`; returned images follow new order and exactly the selected existing image is primary. |
| IMG-04 | Reorder with duplicate image/order, negative order, foreign image ID, or primary ID from another variant. | Duplicate/negative: `400`; image not belonging to variant: `404`. |
| IMG-05 | `DELETE /admin/variant-images/{id}` then GET/inspect Cloudinary mock. | `204`; image metadata and Cloudinary asset are permanently deleted. A second delete is `404` (unlike soft deletes). |
| IMG-06 | Call legacy `GET /variant-images/{id}/content` for legacy local asset and Cloudinary-only asset. | Local asset returns `200`, octet stream and `Cache-Control:max-age=300`; Cloudinary-only/nonexistent asset returns `404`. |

## 5. Cart and merge

For anonymous cart calls put `guest_session_key` in JSON (POST/PUT) and `X-Guest-Session-Key` header (GET/DELETE); legacy query `guest_session_key` is accepted only where documented by the route.

| ID | Scenario / request | Expected result and business rule |
|---|---|---|
| CART-01 | `GET /cart` as new customer and new guest. | `200` with `ACTIVE`, empty `items`, total 0; reading does not create a cart. |
| CART-02 | Guest and customer each `POST /cart/items` for V1 quantity 2. | `200`; an active cart is created; current price is stored and total is 200,000. Customer identity wins over an optional guest key in body. |
| CART-03 | Add V1 quantity 2 twice. | One line item with quantity 4, not two lines; add is an upsert/increment. |
| CART-04 | Add/update inactive/nonexistent V2. | `409 VARIANT_NOT_ACTIVE`; cart accepts only active variants on active products. |
| CART-05 | Add 11 V1, update to 11, or add to an existing line above stock. | `409 INSUFFICIENT_STOCK`; cart quantity cannot exceed current available quantity. Cart does not reserve stock. |
| CART-06 | Add/update quantity 0, -1, 1000, decimal/string; invalid or missing guest key. | `400`; quantity is integer 1–999; guest key must match the stated 16–128 pattern when unauthenticated. |
| CART-07 | Admin changes V1 price; GET cart and checkout preview. | Cart response/preview uses current variant price, not stale `last_seen_price_vnd`. |
| CART-08 | Update/remove a cart item using CUSTOMER_B or different guest key. | `404`, not ownership leakage. |
| CART-09 | Update own line, then delete own line. | `200` with recalculated total, then `204`; item is permanently removed. |
| CART-10 | `DELETE /cart/items` for populated cart. | `200`, items empty and total 0; active cart remains. Repeating is safe. |
| CART-11 | Send both header and query guest key with different values, or an unsupported query parameter. | `400 INVALID_REQUEST`. |
| CART-12 | Guest cart has V1 qty 2; CUSTOMER_A has V1 qty 3. Login then `POST /cart/merge {guest_session_key}`. | `200`; customer line is merged to qty 5, current price is used; guest cart becomes `MERGED`. |
| CART-13 | Merge guest cart whose item was made inactive/unavailable, or merged sum exceeds stock. | `409 INVALID_CART_ITEM` or `409 INSUFFICIENT_STOCK`; atomicity: customer cart must not be partially merged and guest cart must remain active. |
| CART-14 | Call merge unauthenticated; merge same guest cart twice. | `401`; first succeeds, second returns unchanged customer cart because guest cart is no longer active. |

## 6. Account checkout, order, status, cancellation, and stock

Account checkout body:
```json
{"checkout_key":"checkout-20261111-001","shipping_address_id":"<addressA>","payment_method":"COD"}
```

| ID | Scenario / request | Expected result and business rule |
|---|---|---|
| ORD-01 | With V1 qty 2 in CUSTOMER_A cart, `GET /checkout/prices`. | `200`; line price/total is recalculated from current product data. Empty cart is `409 EMPTY_CART`. |
| ORD-02 | Checkout using CUSTOMER_A's address and a fresh checkout key. | `201` with order number `SM-XXXXXXXX`, address snapshot, item snapshots, accepted total, and initial history. Cart becomes checked out; V1 stock decreases 10→8 atomically. |
| ORD-03 | Use an address owned by CUSTOMER_B, nonexistent address, or make Identity address client fail. | Checkout fails (typically `404`/upstream error); assert no order, no cart status change, and no inventory reduction. |
| ORD-04 | Retry same `checkout_key` for CUSTOMER_A, including retry after a successful request but lost client response. | `409 DUPLICATE_CHECKOUT`; key is idempotency protection and may be recovered, not reused. |
| ORD-05 | `POST /checkout/recover` with `{ "checkout_key": ... }` by owner vs CUSTOMER_B. | Owner gets `200` same order; other customer gets `404`. |
| ORD-06 | Checkout an empty, merged, or cart whose item was later inactive/out of stock. | `409 EMPTY_CART`, `INVALID_CART_ITEM`, or `INSUFFICIENT_STOCK`; no partial writes. |
| ORD-07 | Create two concurrent checkouts whose total requested V3 qty is 2 while stock is 1. | Exactly one `201`; the other `409 INSUFFICIENT_STOCK`; final quantity is 0, never negative. |
| ORD-08 | After order, change product name, brand, image, variant fields or price. GET order. | Order retains immutable item/address/price snapshots and accepted total. |
| ORD-09 | `GET /orders?status=&page=&size=` as owner; then CUSTOMER_B reads A's ID/list. | Owner sees only own orders and valid filter/pagination; other owner gets `404` for detail and does not see it in list. Invalid status/page/size/unknown query returns `400`. |
| ORD-10 | ADMIN lists and gets orders. | `200`; admin sees orders across customers and may filter by any `OrderStatus`. |
| ORD-11 | Make a non-COD order `PENDING_PAYMENT`, then admin PUT status `PAID`, `PROCESSING`, `SHIPPED`, `COMPLETED`. | Each `200`; only these legal transitions succeed. `completed_at` is set only at `COMPLETED`; each change appends history (from/to, actor, reason, time). |
| ORD-12 | Admin attempts all forbidden transitions: `PENDING_PAYMENT→PLACED/PROCESSING/SHIPPED/COMPLETED`, `PAID→SHIPPED`, `PROCESSING→COMPLETED`, `SHIPPED→PAID`, any transition from COMPLETED/CANCELLED, or same state. | `409 INVALID_ORDER_STATUS_TRANSITION`; order/history unchanged. |
| ORD-13 | For a COD order in `PENDING_PAYMENT`, admin sets any status except CANCELLED. | `409 COD_CONFIRMATION_REQUIRED`; COD must be confirmed by payment flow before operational progression. |
| ORD-14 | Admin sets `PENDING_PAYMENT→CANCELLED` or `PLACED→CANCELLED`. | `200`; stock restores exactly once (e.g. 8→10), history is appended. |
| ORD-15 | Send two simultaneous valid status updates. | At most one succeeds; stale contender is `409 ORDER_STATUS_CHANGED`; prevents lost transition/history. |
| ORD-16 | Owner requests cancellation on PENDING_PAYMENT/PLACED with reason 5–500 chars. | `201`, request status `PENDING`; only one request per order. |
| ORD-17 | Request cancellation on PAID/PROCESSING/SHIPPED/COMPLETED/CANCELLED; send short/blank/overlong reason; CUSTOMER_B requests A's order. | Valid state violation `409 CANCELLATION_NOT_ALLOWED`; malformed reason `400`; other owner `404`. |
| ORD-18 | Submit a second cancellation request for same eligible order. | `409 CANCELLATION_ALREADY_REQUESTED`. |
| ORD-19 | ADMIN reviews PENDING request as `REJECTED` without decision reason, then with reason. | First `400`; second `200`, status `REJECTED`, reviewer/timestamp/reason saved; order and stock unchanged. |
| ORD-20 | ADMIN approves a PENDING request while order is eligible; then attempt to review again. | `200`; request `APPROVED`, order `CANCELLED`, stock restored once, history reason `Cancellation approved`. Repeat is `409 CANCELLATION_ALREADY_REVIEWED`. |
| ORD-21 | Between request and approval move order to a non-cancellable state, then approve. | `409 CANCELLATION_NOT_ALLOWED`; order is not cancelled and inventory is not restored. |

## 7. Order-status state model (mandatory state coverage)

```text
PENDING_PAYMENT ──payment confirmation──> PAID ──> PROCESSING ──> SHIPPED ──> COMPLETED
       │
       └──────────────> CANCELLED

PLACED ────────────────────────────────> PROCESSING ──> SHIPPED ──> COMPLETED
   │
   └───────────────────────────────────> CANCELLED
```

`COMPLETED` and `CANCELLED` are terminal. The service's direct admin transition matrix is:

| From | Allowed next status |
|---|---|
| `PENDING_PAYMENT` | `PAID`, `CANCELLED` |
| `PLACED` | `PROCESSING`, `CANCELLED` |
| `PAID` | `PROCESSING` |
| `PROCESSING` | `SHIPPED` |
| `SHIPPED` | `COMPLETED` |
| `COMPLETED`, `CANCELLED` | none |

Run ORD-11 through ORD-15 as the exhaustive transition suite. `PLACED` is normally reached by the later payment/COD-confirmation integration, not by the admin status endpoint. The checkout implementation starts an order at `PENDING_PAYMENT`; it does not start at `PLACED`.

## 8. Global inventory and seed regression

| ID | Scenario | Expected result and rule |
|---|---|---|
| INV-01 | Seed an empty development DB, restart with seed enabled, then inspect category/brand/product/variant counts and active product list. | Seed data is present and usable for catalog/cart/checkout; repeat startup does not duplicate unique catalog or SKU data. |
| INV-02 | Add V1 to carts for two different customers, then checkout one order. | Stock is global per variant, not customer/cart; both carts see the reduced available quantity. |
| INV-03 | Cart V1, then reduce its quantity below cart demand through admin variant PUT; preview/checkout. | `409 INSUFFICIENT_STOCK`; cart never reserves inventory. |
| INV-04 | Soft-delete product/variant after adding it to cart; preview/checkout. | `409 INVALID_CART_ITEM`; inactive merchandise cannot be bought. |
| INV-05 | For every failure in ORD-03/06/07 and failed cancellation review, query `product_variant.quantity`. | Quantity is unchanged. Allocate/restore belongs to the same transaction as order state change. |

## 9. Ready-made assets and execution order

Existing Postman collections are `US-03.01_categories_brands.postman_collection.json`, `US-03.02-03.04_products_variants_images.postman_collection.json`, and `US-03.05-03.10_cart_order.postman_collection.json`. Import them for happy-path requests, then add the negative/concurrency cases above. Suggested order: `SEC → CAT → PRD/IMG → CART → ORD → INV`.

Automate database assertions for the high-risk tests: CART-13, ORD-03, ORD-07, ORD-14/20, ORD-15, and INV-05. These prove the transaction boundaries, not merely HTTP responses.
