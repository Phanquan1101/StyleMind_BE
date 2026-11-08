# Guest checkout branch handoff

## Scope and parent

The owner explicitly requested pushing guest checkout after the separately pushed Payment JPA/mapper work. Branch feature/guest-checkout starts at f5c8130fed25df77cf1d6b12a414dfebd827ed28 (refactor/payment-jpa-mapper). Its new commit adds only guest behavior and supporting contracts/tests. Merge Payment first, then guest; comparing against the Payment branch shows only this feature delta.

The active owner-approved behavior is in [the guest contract](../planning/GUEST_CHECKOUT_PROPOSAL.md), [restoration mapping](GUEST_CHECKOUT_RESTORATION_REPORT.md) and [guest cart header follow-up](GUEST_CART_HEADER_REPORT.md). Identity is unchanged. Guest carts, direct shipping, session-digest ownership, COD/SePay and owner-scoped order/payment recovery are included. Phone is required trimmed text without numbering-plan checks or OTP. Existing allocation, registered checkout, Admin collection, webhook authentication, financial atomicity and outbox/inbox guards are preserved.

GET/DELETE cart requests also support X-Guest-Session-Key; legacy query clients remain supported, conflicting credentials fail and JWT ownership retains precedence. Cart DTO logging redacts credentials; verbose MVC body dumps are suppressed. FE source files are not in this backend repository and are not part of this push.

Cloudinary/Docker optional-startup edits and the owner's unrelated postman/PAYMENT_FE_RUN_GUIDE.md remain local and are excluded. The Payment-only parent reports remain unchanged. All original main-worktree files and staged/unstaged/untracked work are preserved; no reset/checkout/revert/clean or force push is performed.

## Migration boundary

The owner confirmed Commerce V9 guest checkout was already applied. V9__support_guest_checkout.sql and Payment V2__support_guest_payment.sql are copied without edits. The conflicting image/inventory script is renamed from V9__create_shared_image_and_inventory_tables.sql to V10__create_shared_image_and_inventory_tables.sql, retaining its exact SQL. The upgrade test verifies populated V9 guest/customer rows and historical checksums survive V10. No persistent schema or Flyway history is altered by this branch handoff.

## Exact feature-delta files

- api-gateway/src/test/java/com/stylemind/gateway/GatewayRoutingTests.java
- commerce-service/src/main/java/com/stylemind/commerce/controller/order/GuestOrderController.java
- commerce-service/src/main/java/com/stylemind/commerce/dto/cart/CartResponse.java
- commerce-service/src/main/java/com/stylemind/commerce/dto/order/PaymentConfirmedEvent.java
- commerce-service/src/main/java/com/stylemind/commerce/entity/Order.java
- commerce-service/src/main/java/com/stylemind/commerce/errors/ApiExceptionHandler.java
- commerce-service/src/main/java/com/stylemind/commerce/repository/cart/CartRepository.java
- commerce-service/src/main/java/com/stylemind/commerce/repository/order/OrderRepository.java
- commerce-service/src/main/java/com/stylemind/commerce/repository/order/PaymentEventRepository.java
- commerce-service/src/main/java/com/stylemind/commerce/security/GuestOrderAccess.java
- commerce-service/src/main/java/com/stylemind/commerce/security/SecurityConfiguration.java
- commerce-service/src/main/java/com/stylemind/commerce/service/order/OrderService.java
- commerce-service/src/main/java/com/stylemind/commerce/service/order/impl/CodOrderConfirmationServiceImpl.java
- commerce-service/src/main/java/com/stylemind/commerce/service/order/impl/OrderServiceImpl.java
- commerce-service/src/main/java/com/stylemind/commerce/service/order/impl/PaymentEventServiceImpl.java
- commerce-service/src/main/java/com/stylemind/commerce/validation/cart/CartRequestValidator.java
- commerce-service/src/main/java/com/stylemind/commerce/validation/order/GuestCheckoutValidator.java
- commerce-service/src/main/resources/application.properties
- commerce-service/src/main/resources/db/migration/V10__create_shared_image_and_inventory_tables.sql
- commerce-service/src/main/resources/db/migration/V9__create_shared_image_and_inventory_tables.sql
- commerce-service/src/main/resources/db/migration/V9__support_guest_checkout.sql
- commerce-service/src/test/java/com/stylemind/commerce/BranchCartOrderPostgresIntegrationTests.java
- commerce-service/src/test/java/com/stylemind/commerce/CatalogReferencePostgresIntegrationTests.java
- commerce-service/src/test/java/com/stylemind/commerce/CatalogSeedInventoryPostgresIntegrationTests.java
- commerce-service/src/test/java/com/stylemind/commerce/CheckoutAuthenticationHttpTests.java
- commerce-service/src/test/java/com/stylemind/commerce/CodInboxMigrationPostgresTests.java
- commerce-service/src/test/java/com/stylemind/commerce/GuestCartHeaderTests.java
- commerce-service/src/test/java/com/stylemind/commerce/GuestCheckoutMigrationPostgresTests.java
- commerce-service/src/test/java/com/stylemind/commerce/GuestCheckoutValidationTests.java
- commerce-service/src/test/java/com/stylemind/commerce/InternalVariantLookupPostgresIntegrationTests.java
- commerce-service/src/test/java/com/stylemind/commerce/IsolatedPostgres.java
- commerce-service/src/test/java/com/stylemind/commerce/PaymentEventPostgresIntegrationTests.java
- commerce-service/src/test/java/com/stylemind/commerce/ProductCatalogPostgresIntegrationTests.java
- database/six-service-model/02_commerce.mmd
- database/six-service-model/03_payment.mmd
- docs/governance/CURRENT_BUSINESS_RULES.md
- docs/governance/CURRENT_PROJECT_CONTEXT.md
- docs/planning/GUEST_CHECKOUT_PROPOSAL.md
- docs/qa/COMMERCE_CONFLICT_RESOLUTION_REPORT.md
- docs/qa/GUEST_CART_HEADER_REPORT.md
- docs/qa/GUEST_CHECKOUT_BRANCH_REPORT.md
- docs/qa/GUEST_CHECKOUT_IMPLEMENTATION_REPORT.md
- docs/qa/GUEST_CHECKOUT_REMOVAL_REPORT.md
- docs/qa/GUEST_CHECKOUT_RESTORATION_REPORT.md
- docs/qa/GUEST_PHONE_SIMPLIFICATION_REPORT.md
- payment-service/src/main/java/com/stylemind/payment/client/RestCommerceOrderClient.java
- payment-service/src/main/java/com/stylemind/payment/dto/OrderPaymentRequest.java
- payment-service/src/main/java/com/stylemind/payment/mapper/PaymentMapper.java
- payment-service/src/main/java/com/stylemind/payment/service/impl/PaymentAttemptServiceImpl.java
- payment-service/src/main/resources/db/migration/V2__support_guest_payment.sql
- payment-service/src/test/java/com/stylemind/payment/GuestPaymentMigrationPostgresTests.java
- payment-service/src/test/java/com/stylemind/payment/PaymentOrderAttemptServiceTests.java
- payment-service/src/test/java/com/stylemind/payment/PaymentOwnerContractTests.java
- payment-service/src/test/java/com/stylemind/payment/PaymentRuntimePostgresIntegrationTests.java
- postman/GUEST_CHECKOUT_RUN_GUIDE.md
- postman/StyleMind-Guest-Checkout.postman_collection.json
- scripts/validate-guest-postman.cjs

Git recognizes the image/inventory rename from the deletion/addition pair above. JPA repository/entity implementation and the parent's isolated Payment helper are unchanged relative to f5c8130.

## Verification on this branch

- commerce: BUILD SUCCESS; 94 tests, zero failures/errors/skips.
- payment: BUILD SUCCESS; 85 tests, zero failures/errors/skips.
- gateway: BUILD SUCCESS; 12 tests, zero failures/errors/skips.

Total: 191 tests passed on the exact feature branch. Postman structural validation passed for nine requests and ten script blocks. git diff --check passed. Expected negative cases deliberately log test-only outages/rollback exceptions; these are not test failures.

Commands from the guest worktree:

~~~powershell
mvn -o -B -ntp -f commerce-service/pom.xml '-Ddebug=false' '-DargLine=-Duser.timezone=UTC' '-Dstylemind.test.postgres-bin=C:/Program Files/PostgreSQL/18/bin' verify
mvn -o -B -ntp -f payment-service/pom.xml '-Ddebug=false' '-DargLine=-Duser.timezone=UTC' '-Dstylemind.test.postgres-bin=C:/Program Files/PostgreSQL/18/bin' verify
mvn -o -B -ntp -f api-gateway/pom.xml '-Ddebug=false' verify
node scripts/validate-guest-postman.cjs
git diff --cached --check
~~~

Logs are ignored guest-commerce-verify.log, guest-payment-verify.log and guest-gateway-verify.log in this worktree, plus module Surefire XML reports. The importable Postman collection and Vietnamese FE/run guide are in postman/StyleMind-Guest-Checkout.postman_collection.json and postman/GUEST_CHECKOUT_RUN_GUIDE.md. Structural/script validation is separate from HTTP execution; no live/manual Postman collection run is claimed.

Tests use only synthetic fixtures and temporary PostgreSQL 18.1 clusters on random loopback ports under ignored module target/isolated-postgres directories. Helpers stop their own clusters. No owner database, Compose service, ignored secret, real payment/provider operation or Identity file is accessed. The configured read-only backlog workbook is unavailable; it is not edited. All three JARs are built, but no combined packaged-service smoke is claimed. Local checks are not production readiness; production deployment timing and previously out-of-scope guest claiming/refunds/paid cancellation remain outside this push.
