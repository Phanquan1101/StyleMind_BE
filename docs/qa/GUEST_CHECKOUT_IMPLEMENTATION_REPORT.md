# Guest checkout implementation report

> WITHDRAWN: the owner temporarily removed guest checkout on 2026-11-02. Guest cart remains; checkout requires login. Historical implementation/test evidence below is not an active API contract. See [removal report](../qa/GUEST_CHECKOUT_REMOVAL_REPORT.md).


Date: 2026-11-02 (Asia/Saigon).

## Scope and approval

The owner approved the complete [guest contract](../planning/GUEST_CHECKOUT_PROPOSAL.md), including exact Commerce V9 and Payment V2 migration scope, after explicitly excluding Identity changes. Guest checkout uses existing guest carts and direct shipping data; it creates neither Identity accounts nor saved addresses. Existing COD and SePay settlement contracts remain in force. No persistent database migration/deployment was performed.

Read current context, business rules, open decisions, target models, migrations, relevant contracts and owning tests. The required read-only backlog workbook is absent at C:\Users\FPTSHOP\Desktop\StyleMind_Task.xlsx. It was not modified.

## Requirement mapping

| Approved requirement | Implementation / evidence |
| --- | --- |
| Guest cart checkout without JWT | Four narrowly permitted method/path combinations under /api/v1/guest. GuestOrderController uses existing session possession via X-Guest-Session-Key. No generic permitAll for orders or APIs. |
| Guest ownership | Commerce stores SHA-256(session key) in guest_session_hash. Foreign/missing guest orders return the same 404. Neither key nor digest is exposed in Order/Payment responses/events. Ordinary customer order/cancellation routes deny guest orders; Admin fulfilment remains. |
| Direct shipping without Identity | GuestCheckoutValidator requires exact known fields; Unicode trim, name 1-100/code points, full address 1-500, control rejection; Per owner follow-up, phone is required trimmed text of 1-64 code points without controls; no country/prefix validation or automatic E.164 conversion. The temporary libphonenumber dependency has been removed from Commerce. Existing address snapshot stores source_address_id NULL. No account/email/OTP/address-book feature is added. |
| Physical ownership model | Commerce V9 permits nullable customer_id, enforces exactly one customer/digest owner and guest-scoped checkout-key uniqueness, permits nullable inbox owner. Payment V2 permits nullable attempt owner. Existing rows and applied migrations unchanged. Logical Mermaid model sources updated; rendered diagram exports were not regenerated. |
| Price and atomicity | Server revalidates active variant prices. Cart lock reads scalar current state after waiting, preventing two concurrent checkouts from consuming one cart despite JPA first-level caching. Order/address/items/history/cart commit together; injected persistence failure rolls back and allows retry. |
| COD and SePay handoff | Existing after-commit credential-authenticated handoff carries explicit customer_id NULL. Snapshot parser rejects missing/malformed IDs, invalid amount/state/method. Replay and public ownership checks use null-safe equality; null is not a wildcard. |
| Settlement | PaymentMapper preserves explicit null in outbox JSON. COD confirmation and Commerce inbox compare owners null-safely; guest/customer mismatch is rejected/reconciled. Existing immutable single ledger/outbox, row locks, exact amount, Admin-only cash receipt, authenticated webhook, duplicate/terminal handling and event retry remain. |
| Recovery and private responses | Owner-scoped payment-attempt endpoint retrieves/retries the same initial key after handoff failure. No Identity/Notification transaction. Guest responses/errors use no-store; unexpected guest request handler logs correlation only, not exception content. |
| Gateway / FE handoff | Existing /commerce prefix forwards guest header/body without JWT. Routing test added; production gateway configuration unchanged. Collection and run guide specify FE requests. FE source is not in this repository. |

## Verification

| Command / check | Result |
| --- | --- |
| Commerce full verify, native isolated PostgreSQL | BUILD SUCCESS: 82 tests; 0 failures, 0 errors, 0 skips. Initial implementation also passed 81 tests before the additional privacy regression case. |
| Payment full verify, native isolated PostgreSQL | BUILD SUCCESS: 84 tests; 0 failures, 0 errors, 0 skips. Includes the previously unverified JPA repository integration tests. |
| Gateway full verify | BUILD SUCCESS: 12 tests; 0 failures, 0 errors, 0 skips. |
| node scripts/validate-guest-postman.cjs | PASS: 14 requests and 15 JavaScript event scripts parsed; JSON/variables validated. No HTTP requests or Postman scripts executed. |
| node --check scripts/verify-guest-checkout.cjs | PASS. |
| Packaged three-service HTTP/database verification | PASS: four scenario groups across real Commerce, Payment and Gateway JARs; synthetic guest COD/SePay and concurrent checkout. Identity address calls: 0. |
| git diff --check | PASS. |

Commands (PowerShell):

~~~powershell
mvn -o -B -ntp -f commerce-service/pom.xml '-Ddebug=false' '-DargLine=-Duser.timezone=UTC' '-Dstylemind.test.postgres-bin=C:\Program Files\PostgreSQL\18\bin' verify
mvn -o -B -ntp -f payment-service/pom.xml '-Ddebug=false' '-DargLine=-Duser.timezone=UTC' '-Dstylemind.test.postgres-bin=C:\Program Files\PostgreSQL\18\bin' verify
mvn -o -B -ntp -f api-gateway/pom.xml '-Ddebug=false' verify
node scripts/validate-guest-postman.cjs
$env:STYLEMIND_TEST_POSTGRES_BIN = 'C:\Program Files\PostgreSQL\18\bin'
node scripts/verify-guest-checkout.cjs
~~~

Evidence (ignored local artifacts): commerce-service/target/guest-regression.log, commerce-service/target/guest-regression-final.log, payment-service/target/guest-regression.log, api-gateway/target/guest-regression.log and each module's surefire-reports. Final runtime evidence: target/guest-checkout-e2e-final.log and target/guest-checkout-e2e-1793564087348-12676/result.json plus service/PostgreSQL logs. All three final gates passed: 178 tests total, 0 failures/errors/skips.

Automated coverage includes guest name/phone/address validation, unknown/owner/amount input rejection, missing/invalid credentials, guest A/B and customer/Admin isolation, same checkout-key conflicts, concurrent checkout, rollback/retry, handoff outage and recovery, populated-schema upgrades retaining old checksums/data, nullable internal contract/payload, guest COD and online settlement/replay, registered-user regression, existing settlement/outbox/inbox concurrency and failure tests, and Gateway forwarding. The old COD V7-to-V8 test now explicitly targets V8 so it continues to test that historical upgrade separately from V9.

## Packaged runtime results

- Guest COD creation/replay/direct shipping, missing/wrong key and authenticated-customer isolation; no financial credit before collection.
- Admin-only COD collection; repeated collection rejected; one ledger/outbox and applied Commerce inbox; fulfilment stays PLACED.
- Guest SePay QR retrieval; authenticated synthetic callback/repeated callback; one financial credit and Commerce PAID through outbox/inbox.
- Six simultaneous HTTP checkouts consume one guest cart once.
- Identity and Notification services were not started. The synthetic JWKS fixture recorded zero address calls. PostgreSQL reports 18.1; no persistent database was touched.

## Environment and limitations

Docker was unavailable. Instead, explicit opt-in test helpers started fresh native PostgreSQL 18.1 clusters under the modules' ignored target/isolated-postgres directories, on random loopback ports, with synthetic logins and trust authentication restricted to those disposable local clusters. No connection to an owner's existing PostgreSQL data directory/database occurred. Helpers remove inherited libpq configuration and stop only their own clusters; default tests still use Testcontainers PostgreSQL 18.6 when the native opt-in property is absent. Native test data/log directories remain ignored local evidence, not application data. Native smoke resources similarly live only under ignored target/guest-checkout-e2e-*.

The first native test initialization wrote a log inside the intended data directory; initdb rejected the non-empty directory. The helper now keeps logs outside data and the repeated test passed. The first packaged harness run encountered a Windows pg_ctl inherited-pipe wait before any service launched; its own Node process and cluster were stopped, and command output was moved to files before retry. These attempts are not counted as passed runtime verification.

No Compose, real bank transfer/provider callback, external SMTP, actual cash receipt, production deployment, owner data update, secret-file change, commit or push. Identity source/migrations are unchanged; earlier staged/unstaged/untracked work, including Payment JPA/mapper artifacts and postman/PAYMENT_FE_RUN_GUIDE.md, is preserved. No manual Postman execution was performed; structural validation is explicitly separate from HTTP integration execution.

FE must call the new guest APIs and retain a securely random guest key. Existing catalog browsing permissions are unchanged. There is no new account claiming, guest cancellation, session-loss recovery, guest listing, phone verification, refund or Notification feature. Broader production abuse controls, deployment/provider setup and existing cancellation/payment exception decisions remain outside this approved change. No new owner approval remains pending for the implemented scope. Local evidence does not establish production readiness.

## Exact files added or changed for this task

- commerce-service/pom.xml
- commerce-service/src/main/java/com/stylemind/commerce/controller/order/GuestOrderController.java
- commerce-service/src/main/java/com/stylemind/commerce/dto/order/PaymentConfirmedEvent.java
- commerce-service/src/main/java/com/stylemind/commerce/entity/Order.java
- commerce-service/src/main/java/com/stylemind/commerce/errors/ApiExceptionHandler.java
- commerce-service/src/main/java/com/stylemind/commerce/repository/cart/CartRepository.java
- commerce-service/src/main/java/com/stylemind/commerce/repository/order/OrderRepository.java
- commerce-service/src/main/java/com/stylemind/commerce/repository/order/PaymentEventRepository.java
- commerce-service/src/main/java/com/stylemind/commerce/security/GuestOrderAccess.java
- commerce-service/src/main/java/com/stylemind/commerce/security/SecurityConfiguration.java
- commerce-service/src/main/java/com/stylemind/commerce/service/order/CodOrderConfirmationService.java
- commerce-service/src/main/java/com/stylemind/commerce/service/order/OrderService.java
- commerce-service/src/main/java/com/stylemind/commerce/service/order/PaymentEventService.java
- commerce-service/src/main/java/com/stylemind/commerce/validation/order/GuestCheckoutValidator.java
- commerce-service/src/main/resources/db/migration/V9__support_guest_checkout.sql
- payment-service/src/main/java/com/stylemind/payment/client/RestCommerceOrderClient.java
- payment-service/src/main/java/com/stylemind/payment/dto/OrderPaymentRequest.java
- payment-service/src/main/java/com/stylemind/payment/mapper/PaymentMapper.java
- payment-service/src/main/java/com/stylemind/payment/service/impl/PaymentAttemptServiceImpl.java
- payment-service/src/main/resources/db/migration/V2__support_guest_payment.sql
- commerce-service/src/test/java/com/stylemind/commerce/IsolatedPostgres.java
- commerce-service/src/test/java/com/stylemind/commerce/GuestCheckoutValidationTests.java
- commerce-service/src/test/java/com/stylemind/commerce/GuestOrderHttpTests.java
- commerce-service/src/test/java/com/stylemind/commerce/GuestCheckoutMigrationPostgresTests.java
- commerce-service/src/test/java/com/stylemind/commerce/BranchCartOrderPostgresIntegrationTests.java
- commerce-service/src/test/java/com/stylemind/commerce/CatalogReferencePostgresIntegrationTests.java
- commerce-service/src/test/java/com/stylemind/commerce/CodInboxMigrationPostgresTests.java
- commerce-service/src/test/java/com/stylemind/commerce/InternalVariantLookupPostgresIntegrationTests.java
- commerce-service/src/test/java/com/stylemind/commerce/PaymentEventPostgresIntegrationTests.java
- commerce-service/src/test/java/com/stylemind/commerce/ProductCatalogPostgresIntegrationTests.java
- payment-service/src/test/java/com/stylemind/payment/IsolatedPostgres.java
- payment-service/src/test/java/com/stylemind/payment/GuestPaymentContractTests.java
- payment-service/src/test/java/com/stylemind/payment/GuestPaymentMigrationPostgresTests.java
- payment-service/src/test/java/com/stylemind/payment/OrderPaymentPostgresIntegrationTests.java
- payment-service/src/test/java/com/stylemind/payment/PaymentCreationHttpPostgresTests.java
- payment-service/src/test/java/com/stylemind/payment/PaymentEventDispatcherPostgresIntegrationTests.java
- payment-service/src/test/java/com/stylemind/payment/PaymentRuntimePostgresIntegrationTests.java
- payment-service/src/test/java/com/stylemind/payment/PaymentOrderAttemptServiceTests.java
- api-gateway/src/test/java/com/stylemind/gateway/GatewayRoutingTests.java
- database/six-service-model/02_commerce.mmd
- database/six-service-model/03_payment.mmd
- docs/planning/GUEST_CHECKOUT_PROPOSAL.md
- docs/governance/CURRENT_PROJECT_CONTEXT.md
- docs/governance/CURRENT_BUSINESS_RULES.md
- postman/StyleMind-Guest-Checkout.postman_collection.json
- postman/GUEST_CHECKOUT_RUN_GUIDE.md
- scripts/validate-guest-postman.cjs
- scripts/verify-guest-checkout.cjs
- docs/qa/GUEST_CHECKOUT_IMPLEMENTATION_REPORT.md

## Owner follow-up: simplified guest phone input

The owner requested removing strict phone validation. The current guest contract and run guide now use bounded required text without country/prefix checks or normalization. Identity phone handling is unchanged. See [follow-up verification](GUEST_PHONE_SIMPLIFICATION_REPORT.md); the 178-test and packaged-service evidence above records the preceding guest-checkout baseline.
