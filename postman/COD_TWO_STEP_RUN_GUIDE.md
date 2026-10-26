# US-04.03 Confirm COD Collection

Import [the eleven-request collection](StyleMind-COD-Two-Step.postman_collection.json). Use disposable local Orders: the Admin request simulates recording a real cash collection and writes ledger data; it does not contact a bank. Exported credential defaults are empty. Manual Postman execution is separate from automated verification.

## Setup and run

1. Rebuild Commerce and Payment in your development environment after reviewing your existing configuration: `docker compose up -d --build commerce-service payment-service`. This is an operator instruction; implementation tests did not run the owner's Compose stack. Commerce now requires owner-approved V8 (inbox CHECK permits COD and SePay). V7 and Payment V1 are unchanged. Deploy Commerce/V8 before enabling the updated Payment sender; do not edit applied migration history.
2. Set the existing shared `STYLEMIND_COMMERCE_PAYMENT_TOKEN` in Commerce/Payment. This is the creation credential. Also set matching `STYLEMIND_COMMERCE_SERVICE_TOKEN` in both services and `STYLEMIND_PAYMENT_EVENTS_ENABLED=true` in Payment for SePay/COD confirmation delivery. The dispatcher uses the existing poll delay (default 10000 ms). COD does not need a bank webhook. Enabling it also delivers older committed, settled COD/SePay outbox rows.
3. Prepare an active cart and owned shipping address using existing APIs. Set local Current Values for `customerToken`, `adminToken` and `shippingAddressId`. Default bases are Gateway `/commerce` and `/payment` on localhost:8080.
4. Request 1 checks out COD. Successful handoff returns Order PLACED: confirmed, not paid. If Payment is unavailable, the saved Order stays PENDING_PAYMENT; restore Payment and use request 2 to recover, without repeating checkout or changing the initial key.
5. Request 2 returns the pending COD attempt and captures its ID. It has no QR/expiry and has not created a financial transaction. Request 3 proves a Customer cannot collect; request 4 rejects amount 1 (prepare an Order worth more than 1 VND).
6. Request 5 records a synthetic cash collection as Admin and returns payment status PAID; its exact amount is captured from the accepted Order. Request 6 repeats and expects 409. Request 7 rejects a fractional amount without rounding. Request 8 confirms fulfilment remains PLACED in this untouched fixture. Requests 9/10 check the Admin exception list and deny Customer access. Request 11 retrieves the same PAID attempt through owner recovery and cannot collect cash a second time.

PENDING_PAYMENT on a new COD Order means its payment obligation has not yet been acknowledged; it is not a request to display a bank QR. PLACED means ready for fulfilment and does not imply cash has been received. After Admin collection, Payment stores SETTLED/returns PAID; the Order continues its own fulfilment state machine.

Only an Admin who has verified actual collection should use the collection endpoint outside disposable tests. There is no automatic bank confirmation for COD and no requirement to manually approve an Order at checkout. The system cannot independently observe physical cash; the Admin action is the collection record.

## Confirming delivery and failure cases

HTTP 200 collection establishes the local Payment transaction, not immediate Commerce delivery. After the polling delay, inspect the inbox on your disposable Commerce database: one APPLIED COD receipt for this order, reason COD_COLLECTION_CONFIRMED. The Order must keep its fulfilment state and have no extra PAID history row. Payment outbox published_at is set only after a valid matching ACK.

There is no new public payment-status endpoint or Order column. Commerce's durable inbox is the collection record. The collection's Order response alone cannot prove delivery; verify the receipt and outbox in a test environment, or run the two-JAR harness below.

For receiver outage/lost ACK, the committed outbox remains pending and retries the same event ID; no second collection call is needed. The harness simulates 503 then loses a committed COD ACK and checks one receipt/ledger. Missing, foreign, amount/method-mismatched, CANCELLED or PENDING_PAYMENT Orders produce reconciliation receipts; Payment stays SETTLED and creates ORDER_PAYMENT_REJECTED for the Admin list. A COMPLETED COD order can acknowledge a cash receipt without being reopened or changing its completed state.

No Notification service is called by the sender/receiver and no notification delivery controls transaction success. If your database already has a different V8, resolve that version conflict before rollout; never delete volumes or repair blindly. V8 approval is schema scope only, not permission to change an owner's persistent database during tests.

## Verification

```powershell
mvn -o -B -ntp -f commerce-service/pom.xml '-Ddebug=false' '-DargLine=-Duser.timezone=UTC' verify
mvn -o -B -ntp -f payment-service/pom.xml '-Ddebug=false' '-DargLine=-Duser.timezone=UTC' verify
node scripts/validate-cod-postman.cjs
# After regression passes: two fresh databases/actual JARs, synthetic JWTs, service and Identity fixtures.
node scripts/verify-cod-two-step.cjs
```

[The implementation report](../docs/qa/COD_TWO_STEP_REPORT.md) records actual command results and changed files. The Postman validator only checks JSON/templates and script syntax. The two-JAR harness uses temporary service databases, fake Identity/JWKS and an upstream-failure fixture; it never uses a real bank, owner credentials or persistent database.

Remaining scope: durable Commerce-to-Payment delivery after process crash, notification/email, best-effort cart redesign, refund/paid cancellation and manual reconciliation are not added by these two steps. Existing cart checkout remains in the Order transaction; existing SePay behavior is retained.
