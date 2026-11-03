# Temporary removal of guest checkout

> Historical removal report. The owner subsequently requested restoring guest checkout; current behavior and verification are in [the restoration report](GUEST_CHECKOUT_RESTORATION_REPORT.md).

Date: 2026-11-02 (Asia/Saigon).

## Owner instruction and current behavior

The owner explicitly chose: remove only the newly added guest checkout; keep the existing guest cart. Payment JPA/mapper work and all unrelated staged/unstaged/untracked files are preserved. Identity is unchanged.

Removed the guest checkout controller, validator, ownership helper, OrderService guest methods and guest repository/entity accessors. Removed the guest route permitAll exceptions. The old /api/v1/guest order/price/payment endpoints no longer exist; anonymous requests are denied by the normal API authentication rule, and authenticated requests to removed GET routes return 404. Registered checkout continues through the original saved-address contract.

New internal Payment order-attempt snapshots require a non-null valid customer UUID, both in JSON parsing and in service validation before any repository access. Guest order/payment creation is no longer supported.

## Retained compatibility and data boundaries

- Pre-existing guest cart GET/add/update/delete/clear and authenticated merge are unchanged.
- Commerce V9 and Payment V2 remain byte-for-byte unchanged. Whether the owner has already applied them through Docker is unknown. Deleting these files or reverting schema constraints could invalidate migration history or existing rows; no database rollback or persistent data mutation was requested/performed.
- Null-safe existing-order authorization, COD confirmation, Payment event/inbox comparison and nullable legacy financial payload serialization remain for records/events that might already exist. This is compatibility for persisted financial truth, not a public guest feature or a new guest creation path.
- Old guest rows are not deleted, reassigned to accounts, reopened or mass-updated. There is no public guest order recovery anymore. Existing Admin fulfilment/collection, provider callback and outbox/inbox mechanisms retain their established authorization and state guards.
- The cart checkout lock/cache fix and opt-in isolated PostgreSQL test helpers remain useful independently of guest checkout and were retained.

## Exact source/artifact changes in this removal

Removed:

- commerce-service/src/main/java/com/stylemind/commerce/controller/order/GuestOrderController.java
- commerce-service/src/main/java/com/stylemind/commerce/security/GuestOrderAccess.java
- commerce-service/src/main/java/com/stylemind/commerce/validation/order/GuestCheckoutValidator.java
- commerce-service/src/test/java/com/stylemind/commerce/GuestCheckoutValidationTests.java
- commerce-service/src/test/java/com/stylemind/commerce/GuestOrderHttpTests.java
- postman/StyleMind-Guest-Checkout.postman_collection.json
- scripts/validate-guest-postman.cjs
- scripts/verify-guest-checkout.cjs

Updated:

- commerce-service/src/main/java/com/stylemind/commerce/service/order/OrderService.java
- commerce-service/src/main/java/com/stylemind/commerce/security/SecurityConfiguration.java
- commerce-service/src/main/java/com/stylemind/commerce/entity/Order.java
- commerce-service/src/main/java/com/stylemind/commerce/repository/order/OrderRepository.java
- commerce-service/src/main/java/com/stylemind/commerce/errors/ApiExceptionHandler.java (removed obsolete guest-only branch)
- payment-service/src/main/java/com/stylemind/payment/dto/OrderPaymentRequest.java
- payment-service/src/main/java/com/stylemind/payment/service/impl/PaymentAttemptServiceImpl.java
- commerce-service/src/test/java/com/stylemind/commerce/BranchCartOrderPostgresIntegrationTests.java (removed guest checkout cases; existing guest cart cases retained)
- payment-service/src/test/java/com/stylemind/payment/PaymentOrderAttemptServiceTests.java
- payment-service/src/test/java/com/stylemind/payment/PaymentRuntimePostgresIntegrationTests.java (new anonymous creation rejection and direct legacy-row financial compatibility coverage)
- api-gateway/src/test/java/com/stylemind/gateway/GatewayRoutingTests.java (removed guest checkout-specific test)
- database/six-service-model/02_commerce.mmd
- database/six-service-model/03_payment.mmd (retained schema fields now labelled legacy)
- docs/planning/GUEST_CHECKOUT_PROPOSAL.md
- docs/qa/GUEST_CHECKOUT_IMPLEMENTATION_REPORT.md
- docs/qa/GUEST_PHONE_SIMPLIFICATION_REPORT.md (historical documents marked withdrawn)
- docs/governance/CURRENT_PROJECT_CONTEXT.md
- docs/governance/CURRENT_BUSINESS_RULES.md
- postman/GUEST_CHECKOUT_RUN_GUIDE.md (replaced with removal notice and existing-cart/authenticated-checkout guidance)
- docs/qa/GUEST_CHECKOUT_REMOVAL_REPORT.md (this report)

Renamed/replaced:

- payment-service/src/test/java/com/stylemind/payment/GuestPaymentContractTests.java -> PaymentOwnerContractTests.java; now requires a customer owner for new snapshots while checking legacy outbox serialization.

Added:

- commerce-service/src/test/java/com/stylemind/commerce/CheckoutAuthenticationHttpTests.java; checks removed routes, required login, guest cart availability and absence of guest checkout in runtime OpenAPI.

Removed only the known generated .class files corresponding to deleted sources under target/classes and target/test-classes, preventing stale compiled controllers/tests from remaining in the rebuilt application. No broad clean/reset/checkout/revert operation was used. Existing JPA/mapper reports, repositories/entities, other source changes, and the owner's untracked postman/PAYMENT_FE_RUN_GUIDE.md were preserved.

## Verification

~~~powershell
mvn -o -B -ntp -f commerce-service/pom.xml '-Ddebug=false' '-DargLine=-Duser.timezone=UTC' '-Dstylemind.test.postgres-bin=C:\Program Files\PostgreSQL\18\bin' verify
mvn -o -B -ntp -f payment-service/pom.xml '-Ddebug=false' '-DargLine=-Duser.timezone=UTC' '-Dstylemind.test.postgres-bin=C:\Program Files\PostgreSQL\18\bin' verify
mvn -o -B -ntp -f api-gateway/pom.xml '-Ddebug=false' verify
~~~

- Commerce: BUILD SUCCESS; 72 tests, 0 failures/errors/skips.
- Payment: BUILD SUCCESS; 85 tests, 0 failures/errors/skips.
- Gateway: BUILD SUCCESS; 11 tests, 0 failures/errors/skips.
- Packaged JAR absence of deleted guest controller/helper/validator: PASS (jar tf verification).
- Existing authenticated-checkout Postman collection JSON: PASS, 8 requests parsed with collection v2.1 structure; no manual Postman/HTTP collection execution claimed.
- git diff --check: PASS. Total: 168 tests passed, no skipped tests.

Evidence: each module's ignored target/guest-checkout-removal.log and surefire-reports. Tests use fresh native PostgreSQL 18.1 clusters in ignored target/isolated-postgres on random loopback ports with synthetic fixtures. Existing schema compatibility upgrade tests remain; new anonymous snapshots are rejected and previously persisted anonymous SePay/COD financial records can still settle under their established authorization. No Compose or owner database was accessed or migrated. No packaged guest checkout smoke is appropriate after removing that feature.

No commit/push, Identity change, ignored-secret read/change, real bank transfer or provider deployment. The read-only backlog workbook is unavailable at its configured path; it was not edited. Local tests do not establish production readiness. Schema rollback/guest-data deletion is outside this removal and is not implicitly authorized.
