# Guest checkout restoration — 2026-11-02

## Authorization and scope

The owner requested restoring guest checkout after its temporary removal and confirmed that Commerce V9 guest checkout was already applied. The [guest contract](../planning/GUEST_CHECKOUT_PROPOSAL.md), including the simplified phone rule, is active again. Identity, synthetic accounts, guest cancellation/account claiming, refunds and persistent database operations remain excluded. Existing Payment JPA/mapper work and upstream inventory functionality are preserved. No new persistence framework or dependency was introduced.

Commerce V9 guest SQL and Payment V2 SQL were retained unchanged. The unexecuted, conflicting `V9__create_shared_image_and_inventory_tables.sql` was renamed `V10__create_shared_image_and_inventory_tables.sql` without changing SQL. Comparison with tracked upstream SQL passed. Only its stale generated V9 copy under `commerce-service/target/classes/db/migration` was removed, preventing Maven resource copying from retaining two versions of that script. No applied migration was rewritten or Flyway history repaired.

## Requirement mapping

All rules below come from the latest owner restoration request and the guest contract, rather than archived documents or inference from source behavior.

| Requirement | Model/API/validation | Authorization, transaction and effects |
| --- | --- | --- |
| Guest prices and checkout | GET `/api/v1/guest/checkout/prices`; POST `/api/v1/guest/orders`; strict checkout key, COD/SEPAY_VIETQR and direct shipping object | Existing guest cart key; row lock and active-state recheck; server prices; one local transaction saves order/address/items, preserves existing allocation and checks out cart |
| Direct shipping without Identity | `customer_order.customer_id=NULL`, `guest_session_hash=SHA-256(key)`; snapshot `source_address_id=NULL` | No Identity call; required trimmed name/phone/address; phone is text of 1–64 code points, no numbering-plan normalization or OTP |
| Owner-scoped detail | GET `/api/v1/guest/orders/{orderId}` | Valid `X-Guest-Session-Key`; query requires both null customer and matching digest; foreign/missing order returns 404; missing/malformed credential returns 401; response never exposes key/digest |
| Payment handoff/recovery | POST `/api/v1/guest/orders/{orderId}/payment-attempt`, no request body | Detail ownership checked before Payment call; deterministic initial-order key; handoff after checkout commit; failures leave order saved; terminal state rejected; COD confirmation uses separate local transaction |
| Financial compatibility | Existing credential-authenticated internal Payment snapshot explicitly permits null customer; omission/malformed owner rejected | Public customer authorization unchanged; replay compares nullable owners safely; Admin-only COD collection and authenticated SePay callback/outbox/inbox remain; no guest key sent to Payment |
| Customer/Admin isolation | Existing customer order GET/cancellation/recovery remain self-only; Admin fulfilment can view guest orders | JWT does not replace guest credential; ordinary customer cannot access guest orders; no automatic account linking or guest list endpoint |
| Private response caching | Guest success and handled application-error responses use `Cache-Control: no-store` | Exact guest method/path exceptions only; existing Admin and authenticated APIs remain protected |

## Exact files changed in this restoration

Commerce source additions:

- `commerce-service/src/main/java/com/stylemind/commerce/controller/order/GuestOrderController.java`
- `commerce-service/src/main/java/com/stylemind/commerce/security/GuestOrderAccess.java`
- `commerce-service/src/main/java/com/stylemind/commerce/validation/order/GuestCheckoutValidator.java`

Commerce source updates:

- `commerce-service/src/main/java/com/stylemind/commerce/entity/Order.java`
- `commerce-service/src/main/java/com/stylemind/commerce/repository/order/OrderRepository.java`
- `commerce-service/src/main/java/com/stylemind/commerce/security/SecurityConfiguration.java`
- `commerce-service/src/main/java/com/stylemind/commerce/service/order/OrderService.java`
- `commerce-service/src/main/java/com/stylemind/commerce/service/order/impl/OrderServiceImpl.java`
- `commerce-service/src/main/java/com/stylemind/commerce/errors/ApiExceptionHandler.java`

Commerce migration rename: `commerce-service/src/main/resources/db/migration/V9__create_shared_image_and_inventory_tables.sql` → `V10__create_shared_image_and_inventory_tables.sql` in the same directory. Guest V9 remains unchanged.

Commerce tests:

- `commerce-service/src/test/java/com/stylemind/commerce/BranchCartOrderPostgresIntegrationTests.java`
- `commerce-service/src/test/java/com/stylemind/commerce/CatalogSeedInventoryPostgresIntegrationTests.java` (uses the existing opt-in isolated PostgreSQL factory; default still Testcontainers)
- `commerce-service/src/test/java/com/stylemind/commerce/CheckoutAuthenticationHttpTests.java`
- `commerce-service/src/test/java/com/stylemind/commerce/GuestCheckoutMigrationPostgresTests.java`
- `commerce-service/src/test/java/com/stylemind/commerce/GuestCheckoutValidationTests.java` (added)

Payment source/tests:

- `payment-service/src/main/java/com/stylemind/payment/dto/OrderPaymentRequest.java`
- `payment-service/src/main/java/com/stylemind/payment/service/impl/PaymentAttemptServiceImpl.java`
- `payment-service/src/test/java/com/stylemind/payment/PaymentOrderAttemptServiceTests.java`
- `payment-service/src/test/java/com/stylemind/payment/PaymentRuntimePostgresIntegrationTests.java`
- `payment-service/src/test/java/com/stylemind/payment/PaymentOwnerContractTests.java`

Gateway: `api-gateway/src/test/java/com/stylemind/gateway/GatewayRoutingTests.java` adds actual HTTP forwarding evidence for guest headers/body and absence of Identity calls; production routing is unchanged.

Contracts, models and handoff artifacts:

- `database/six-service-model/02_commerce.mmd`
- `database/six-service-model/03_payment.mmd`
- `docs/governance/CURRENT_PROJECT_CONTEXT.md`
- `docs/governance/CURRENT_BUSINESS_RULES.md`
- `docs/planning/GUEST_CHECKOUT_PROPOSAL.md`
- `docs/qa/GUEST_CHECKOUT_REMOVAL_REPORT.md` (historical status link)
- `docs/qa/COMMERCE_CONFLICT_RESOLUTION_REPORT.md` (migration follow-up link)
- `docs/qa/GUEST_CHECKOUT_RESTORATION_REPORT.md` (this report)
- `postman/StyleMind-Guest-Checkout.postman_collection.json` (added)
- `postman/GUEST_CHECKOUT_RUN_GUIDE.md` (restored instructions and FE integration guide)
- `scripts/validate-guest-postman.cjs` (added)

All earlier staged/unstaged/untracked work, including mapper/entities, null-safe confirmation/inbox handling and the owner's `postman/PAYMENT_FE_RUN_GUIDE.md`, was preserved. Git's index was not modified; no reset/checkout/revert/clean/commit/push was performed.

## Commands and evidence

```powershell
mvn -o -B -ntp -f commerce-service/pom.xml '-Ddebug=false' '-DargLine=-Duser.timezone=UTC' '-Dstylemind.test.postgres-bin=C:\Program Files\PostgreSQL\18\bin' verify
mvn -o -B -ntp -f payment-service/pom.xml '-Ddebug=false' '-DargLine=-Duser.timezone=UTC' '-Dstylemind.test.postgres-bin=C:\Program Files\PostgreSQL\18\bin' verify
mvn -o -B -ntp -f api-gateway/pom.xml '-Ddebug=false' verify
mvn -o -B -ntp -f commerce-service/pom.xml '-Ddebug=false' '-DargLine=-Duser.timezone=UTC' '-Dstylemind.test.postgres-bin=C:\Program Files\PostgreSQL\18\bin' '-Dtest=CheckoutAuthenticationHttpTests,GuestCheckoutMigrationPostgresTests' verify
node scripts/validate-guest-postman.cjs
git diff --check
```

- Full Commerce gate: BUILD SUCCESS, 85 tests, zero failures/errors/skips.
- Full Payment gate: BUILD SUCCESS, 85 tests, zero failures/errors/skips.
- Full Gateway gate: BUILD SUCCESS, 12 tests, zero failures/errors/skips.
- Follow-up Commerce verification after adding the guest error cache header and populated V9→V10 assertion: BUILD SUCCESS, 7 tests, zero failures/errors/skips. Includes one additional HTTP negative-input test; across runs there are 183 distinct tests (189 executions, with six repeats).
- Postman structural validation: PASS, nine requests, ten script blocks compile. This is local JSON/JavaScript validation, not collection HTTP execution or remote JSON-schema validation.
- `git diff --check`: exit 0. Conflict marker scan: no remaining source markers.

Logs: each service's ignored `target/guest-checkout-restoration.log`, plus Commerce `target/guest-checkout-restoration-followup.log` and Surefire XML reports.

Evidence covers guest COD/SePay checkout, direct shipping, no Identity calls, foreign guest/customer denial and Admin access, duplicate checkout, concurrent one-cart consumption, persistence rollback, saved order after payment outage, guest nullable-owner attempt creation/replay/mismatch, both settlement methods, webhook replay, existing inbox/ledger guards and registered-user regression. Intentional outage/rollback/security negative cases log errors by design; test summaries above contain no failures/errors/skips.

## Environment and limits

Only disposable PostgreSQL 18.1 clusters in ignored `target/isolated-postgres` directories on random loopback ports were used. Test databases contain synthetic fixtures and are stopped by the test helper. No owner database, Compose resources, ignored secrets, real bank transfers or Identity files were touched. The supplied backlog workbook path `C:\Users\FPTSHOP\Desktop\StyleMind_Task.xlsx` is absent; no workbook changes were made.

HTTP evidence comes from MockMvc Commerce integration tests, Payment random-port runtime tests and actual Gateway HTTP tests with a recording upstream. Three packaged services were built, but no combined packaged-service guest smoke or manual Postman execution is claimed. Local tests do not establish production readiness. FE implementation is outside this repository; the run guide provides its exact request flow. Deployment timing, provider configuration, guest account claiming/recovery beyond possession of the key, refunds and paid cancellation remain outside this restoration/TBD.


## Separate guest branch handoff

The owner subsequently requested pushing this backend feature on feature/guest-checkout, based on the separately pushed Payment JPA/mapper commit f5c8130. See [branch handoff evidence](GUEST_CHECKOUT_BRANCH_REPORT.md) for the exact feature delta and verification of the branch being published. Earlier runtime/FE observations above remain historical; this push does not modify sibling FE code or the owner database.
