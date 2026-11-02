# Commerce conflict resolution — 2026-11-02

> Follow-up: the owner confirmed guest V9 was applied and requested restoring guest checkout. The duplicate migration is resolved by preserving guest V9 and renumbering the unexecuted image/inventory SQL to V10. See [restoration evidence](GUEST_CHECKOUT_RESTORATION_REPORT.md).

Scope: resolve the owner-reported stash/merge markers while preserving the existing interface/implementation refactor, guest checkout removal, guest cart, Payment JPA/mapper changes and upstream inventory code. No API contract or schema change.

## Files changed

- `commerce-service/src/main/java/com/stylemind/commerce/service/order/CodOrderConfirmationService.java`: retain interface signature.
- `commerce-service/src/main/java/com/stylemind/commerce/service/order/OrderService.java`: retain interface signatures, including cancellation methods.
- `commerce-service/src/main/java/com/stylemind/commerce/service/order/PaymentEventService.java`: retain interface signature.
- `commerce-service/src/main/java/com/stylemind/commerce/service/order/impl/CodOrderConfirmationServiceImpl.java`: preserve incoming null-safe legacy ownership comparison in the implementation.
- `commerce-service/src/main/java/com/stylemind/commerce/service/order/impl/OrderServiceImpl.java`: preserve incoming self-only/null ownership guards and active-cart scalar row lock. Preserve upstream inventory operations.
- `commerce-service/src/main/java/com/stylemind/commerce/service/order/impl/PaymentEventServiceImpl.java`: preserve incoming null-safe event ownership comparison and upstream inventory operation.
- `commerce-service/src/test/java/com/stylemind/commerce/CodInboxMigrationPostgresTests.java`: retain explicit V7-to-V8 test target and one-migration assertion.
- This report.

## Verification

Command: `mvn -o -B -ntp -f commerce-service/pom.xml -Ddebug=false -Dtest=CheckoutAuthenticationHttpTests,OrderPaymentHandoffTests,OrderPaymentRecoveryHttpTests,PaymentClientHttpTests,PaymentServiceSecurityTests test`

Result: BUILD SUCCESS; 15 tests, zero failures/errors/skips. Main and test source compilation passed. Log: `commerce-service/target/conflict-resolution-tests.log`. The handoff negative-path test deliberately logs a callback exception; Surefire reports no test errors.

`git diff --check` passed. Source marker scan found no remaining conflict markers. Git's index still lists the four original unmerged paths because this operation did not stage or otherwise change the index. No commit/push/reset/checkout/revert/clean was performed.

## Limitations and remaining decision

Full database regression/runtime migration execution is not claimed. Two existing Commerce scripts use version V9: `V9__create_shared_image_and_inventory_tables.sql` and `V9__support_guest_checkout.sql`. This duplicate version blocks Flyway migration discovery; scripts were preserved unchanged. Applied migration history must be established before deciding how to reconcile their versions. No persistent database, Docker resources, secrets or Identity files were touched. Tests used mock HTTP/service dependencies and disabled Flyway; no isolated database was started either.

The configured backlog workbook `C:\Users\FPTSHOP\Desktop\StyleMind_Task.xlsx` is absent in this environment. This repair does not implement a new backlog story or HTTP endpoint; existing Postman contracts remain unchanged.
