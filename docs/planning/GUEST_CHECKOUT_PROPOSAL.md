# Guest checkout - approved implementation contract

> ACTIVE AGAIN: the owner requested restoring this guest checkout contract on 2026-11-02. The simplified phone rule below applies; Identity is unchanged.


Date: 2026-11-02 (Asia/Saigon).

## Approval state

CONFIRMED: the owner requested purchasing without login, explicitly excluded changes to identity-service, and requested implementation in this conversation. Guest cart support already exists in Commerce.

CONFIRMED: the owner explicitly approved the complete contract and exact Commerce V9 / Payment V2 migration scope in this conversation on 2026-11-02. Implementation is authorized; Identity changes and persistent database mutation are excluded. Earlier Payment JPA/mapper work is preserved. The read-only backlog workbook is unavailable at its configured path.

## Approved behavior

- Guest is not an Identity account or role. Do not invent a synthetic Identity customer UUID. Existing authenticated checkout continues to use saved Identity addresses unchanged.
- Commerce uses the existing guest cart session key to locate/lock the cart. Guest checkout accepts name, phone and a complete free-form shipping address; no Identity call or address-book write occurs.
- New guest Order and Payment records have customer_id = NULL. Commerce additionally stores a SHA-256 digest of the cart session key as guest ownership. Order responses and Payment messages never expose that digest/session key.
- Guest access uses X-Guest-Session-Key, matching the existing cart key syntax [A-Za-z0-9._:-]{16,128}; FE must retain a randomly generated high-entropy key, e.g. crypto.randomUUID(). Possession of this key authorizes access to associated guest orders. This inherits the existing guest cart possession model; there is no verified identity or phone-ownership guarantee. Missing/invalid credentials receive a sanitized error, and an incorrect valid key cannot distinguish an absent Order from another owner's Order (404).
- Authenticated customer APIs cannot access guest orders, including after logging in. No automatic account linking/claiming is introduced. Existing Admin endpoints can list/view/fulfil guest orders.
- Keep COD and SEPAY_VIETQR. Prices/totals come from Commerce, not the request. Keep initial PENDING_PAYMENT; COD becomes PLACED after an acknowledged pending attempt; only Admin cash collection settles COD. SePay becomes PAID only through the existing authenticated webhook/outbox/inbox flow. No refund, paid cancellation or new payment states.
- Keep order/address/items/cart checkout atomic. Revalidate the cart state after acquiring its lock so concurrent checkout cannot create two orders from the same cart. A failed transaction restores the cart. Payment handoff occurs after commit; failures leave the Order saved for owner-scoped recovery.
- A reused checkout key for the same guest returns existing DUPLICATE_CHECKOUT (409), consistent with current authenticated behavior; a different guest cannot replay or retrieve it. Payment recovery uses the existing deterministic initial-<order_id> key and cannot reopen cancelled orders.

## Approved HTTP contract

Gateway base: /commerce (existing prefixed route already forwards Commerce API paths).

| Method and Commerce path | Purpose / authorization |
| --- | --- |
| GET /api/v1/guest/checkout/prices | Preview existing guest cart; X-Guest-Session-Key required |
| POST /api/v1/guest/orders | Checkout existing guest cart; X-Guest-Session-Key required; 201 + Location |
| GET /api/v1/guest/orders/{order_id} | Guest-owned order detail; key required; no public order list |
| POST /api/v1/guest/orders/{order_id}/payment-attempt | Owner-scoped recovery/QR retrieval; key required; no body |

Only these exact guest method/path combinations become permitAll with guest ownership checks. Existing authenticated and Admin API authorization remains. Private order/payment responses use Cache-Control: no-store. Do not log guest credentials, recipient data or bodies. Existing gateway forwarding supports the header; test that behavior and change routing only if necessary. This repository contains no FE checkout implementation.

Approved checkout body (all top-level and shipping properties required; unknown fields rejected):

~~~json
{
  "checkout_key": "guest-checkout-0001",
  "payment_method": "COD",
  "shipping_address": {
    "recipient_name": "Guest Buyer",
    "recipient_phone": "0912345678",
    "address_line": "1 Nguyen Trai, Ha Noi"
  }
}
~~~

Reuse checkout-key syntax [A-Za-z0-9._:-]{8,128}. Trim Unicode whitespace; name 1-100 code points, complete address 1-500 code points, reject controls/null/blank. Owner follow-up: phone is required text, Unicode-trimmed, 1-64 code points with no remaining controls. Do not validate country/prefix/numbering plan or normalize to +84/E.164; preserve the supplied value after trimming. This supersedes only the earlier guest phone rule; Identity is unchanged. No province/ward lookup, email, OTP, saved address, account creation or phone verification added. Store source_address_id = NULL in the existing order_address_snapshot.

Guest Order responses retain existing fields with customer_id: null. Existing registered customer responses retain non-null IDs.

## Exact approved migration scope

New Commerce V9__support_guest_checkout.sql (do not edit V1-V8):

~~~sql
ALTER TABLE public.customer_order
    ALTER COLUMN customer_id DROP NOT NULL,
    ADD COLUMN guest_session_hash char(64);

ALTER TABLE public.customer_order
    ADD CONSTRAINT customer_order_owner_check CHECK (
        (customer_id IS NOT NULL AND guest_session_hash IS NULL)
        OR (customer_id IS NULL AND guest_session_hash IS NOT NULL)
    );

CREATE UNIQUE INDEX customer_order_guest_checkout_key_uq
    ON public.customer_order (guest_session_hash, checkout_key)
    WHERE guest_session_hash IS NOT NULL;

ALTER TABLE public.payment_event_inbox
    ALTER COLUMN customer_id DROP NOT NULL;
~~~

New Payment V2__support_guest_payment.sql (do not edit V1):

~~~sql
ALTER TABLE public.payment_attempt
    ALTER COLUMN customer_id DROP NOT NULL;
~~~

No Identity change; no existing rows rewritten/deleted; no remote FK; no historical migration changes. Existing rows satisfy the new owner check because customer_id is present and the new column defaults to NULL. Existing uniqueness for authenticated customers remains. No migration is applied to the owner's persistent/local database as part of verification; isolated databases only. Deployment/migration timing needs coordination across Commerce and Payment.

## Internal Payment changes

- Existing credential-authenticated Commerce -> Payment snapshot keeps the same fields but permits explicit customer_id: null for guest orders. Omitted IDs, malformed IDs, invalid amount/method/state remain invalid; non-null UUID behavior remains compatible.
- PaymentConfirmed payload/inbox similarly permits explicit customer_id: null. Use null-safe equality to compare Order/attempt owners, including replay/COD confirmation; guest-vs-customer mismatch still reconciles/rejects. Order ID, method, amount, attempt identity and event replay checks remain required. No guest key goes to Payment.
- Null-safe public ownership checks must continue denying guest Orders/attempts to ordinary authenticated customer routes; never treat null as a wildcard.
- Mapper refactor and JPA changes already requested by the owner are retained. Do not introduce another persistence framework or convert additional repositories in this task.

## Implementation and verification

Add Commerce controller/validator/guest ownership logic, model/repository support, guest checkout and payment recovery, null-safe existing customer/Admin paths, proposed migrations, Payment nullable-owner parsing/mapping and Commerce confirmation/inbox handling. Preserve all existing contracts for registered customers.

Required evidence: positive guest COD and SePay flow; no Identity calls; shipping validation/unknown fields; guest A/B and guest/customer/Admin isolation; missing/wrong keys; correct totals; checkout replay; concurrent cart checkout; transactional rollback; payment timeout/recovery; QR replay and webhook settlement; exactly-once COD collection/outbox/inbox; method/amount/owner mismatch and terminal-state protection; upgrade old populated schemas while retaining existing rows; registered-user regression. Use disposable PostgreSQL only and run existing Commerce/Payment/Gateway regression gates before any optional packaged-JAR smoke. Docker availability is currently unverified for this task; the prior run lacked a daemon, so PostgreSQL tests cannot be assumed to pass.

Deliver an importable guest-checkout Postman collection and run guide, with structural validation reported separately from actual HTTP execution, and an implementation report listing exact changed files, commands/results, limitations and databases touched.


## Restoration and migration ordering — 2026-11-02

The latest owner request restores this contract after its temporary withdrawal. The owner confirmed Commerce V9 guest checkout was already applied. Preserve it byte-for-byte. The pre-existing image/inventory migration formerly also named V9 is now V10, retaining its exact SQL, so both scopes coexist without duplicate versions. This is migration ordering only; no persistent database is accessed or altered by the implementation agent. Payment V2 stays unchanged.
