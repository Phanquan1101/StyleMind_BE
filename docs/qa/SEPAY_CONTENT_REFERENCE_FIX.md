# SePay content reference correction — 2026-11-11

Owner request: fix the observed authenticated inbound webhook with `code: null` and an existing generated payment reference in `content`. The fallback is limited to the configured literal prefix followed by 12 uppercase hex characters, separated from surrounding letters/digits. One distinct candidate is required; repetitions of the same candidate are accepted. Nonblank explicit `code` keeps precedence. No lookup from `description` and no normalization of arbitrary codes are added.

The existing authenticated webhook endpoint, attempt lookup, inbound-only check, exact amount, provider transaction time/expiry, row lock, immutable financial ledger, deduplication and atomic outbox behavior are preserved. Missing/ambiguous content continues through the existing missing-reference exception path. This fixes reference recognition, not manual reconciliation or refunds. Historical exception rows remain intact on a successful retry.

## Files changed

- `payment-service/src/main/java/com/stylemind/payment/service/impl/SepayWebhookServiceImpl.java`: bounded content fallback after authentication.
- `payment-service/src/test/java/com/stylemind/payment/SepayContentReferenceTests.java`: 17 unit cases for fallback, malformed/ambiguous/embedded candidates, literal configured prefix, explicit precedence, unmatched reference, amount and expiry guards.
- `payment-service/src/test/java/com/stylemind/payment/PaymentRuntimePostgresIntegrationTests.java`: authenticated HTTP/PostgreSQL test for null-code fallback, incorrect credentials, concurrent replay, and retry after an existing missing-reference exception; asserts exactly one ledger and outbox and preserved audit exception.
- `docs/planning/US-04.02_CONFIRM_PAYMENT_CONTRACT.md`: owner-requested scoped correction.
- `postman/StyleMind-US-04.02-Confirm-Payment.postman_collection.json`: exact-payment and duplicate fixtures now send null code and a content reference.
- `postman/US-04.02_CONFIRM_PAYMENT_RUN_GUIDE.md`: fallback limits, explicit-code check and provider resend procedure.
- `docs/qa/SEPAY_CONTENT_REFERENCE_FIX.md`: this report.

## Verification and runtime

- `mvn -o -B -ntp -f payment-service/pom.xml '-Ddebug=false' '-DargLine=-Duser.timezone=UTC' verify`: BUILD SUCCESS; 105 tests, 0 failures/errors/skips. Database tests used disposable Testcontainers, not the owner's persistent database. Includes existing authorization, owner isolation, amount/expiry, rollback and concurrency regressions.
- `node scripts/validate-us0402-postman.cjs`: 9 requests, 15 scripts; structural validation and synthetic HMAC/API-key signing checks PASS. This is not an actual Postman HTTP execution.
- `git diff --check`: PASS.
- `docker compose -p stylemind-task up -d --build --no-deps payment-service`: succeeded; only Payment rebuilt/recreated. Runtime log confirms schema up to date, no migration necessary. Payment started and `/payment/v3/api-docs` through local Gateway returned HTTP 200.

No SQL state correction, schema/migration change, environment/secret edit, other-service source change, commit or push was performed. Existing unrelated untracked files were preserved. The required backlog workbook path `C:/Users/FPTSHOP/Desktop/StyleMind_Task.xlsx` is absent on this machine; no workbook was edited. Scope comes from the explicit current owner request and existing confirmation contract.

Live bank settlement after the fix has not been asserted. The owner must resend the original inbound callback from SePay (fresh provider authentication; retain original transaction ID/time/content), then inspect the Order. Do not transfer again or fabricate a replacement provider event. A paid/expired/cancelled/inappropriate order still follows existing guards. No frontend redirect implementation is included: UI navigation requires the frontend to observe the resulting Order status. No production-readiness claim is made.
