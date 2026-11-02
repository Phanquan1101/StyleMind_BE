# COD two-step implementation

The owner requested automatic COD Order confirmation after a pending payment obligation exists, followed separately by Admin-recorded cash collection. In the follow-up reply the owner explicitly chose the existing-schema mapping: **PENDING_PAYMENT -> PLACED**, confirmed but not paid. [Contract](../planning/COD_TWO_STEP_CONTRACT.md).

## Result

1. New COD checkout saves an Order at PENDING_PAYMENT and calls Payment after commit, outside the Commerce transaction. Payment creates/replays a COD attempt at PENDING with no expiry/QR and no financial transaction.
2. A matching saved-attempt acknowledgement triggers a new Commerce transaction: lock/revalidate Order, change PENDING_PAYMENT to PLACED and add one history entry. The checkout response is reread after handoff. Failed/uncertain handoff or failed confirmation leaves a saved pending Order for the existing owner-scoped recovery endpoint. Replays do not duplicate history; cancellation is not reversed.
3. Only Admin POST /api/v1/admin/cod-collections attests actual cash receipt. It locks the pending attempt, validates method/exact whole VND/receipt/time, writes a single financial transaction with Admin identity, settles Payment and writes its local outbox atomically. Customer requests, wrong amount/method, future collection times, fractional coercion and repeated/concurrent collection cannot credit money.
4. Payment SETTLED is returned as PAID. This is distinct from Order fulfilment state, which remains PLACED or its later fulfilment state. No bank webhook, manual Order approval or SHIPPED-only prerequisite is introduced for COD.

There is no new migration or CONFIRMED enum. Existing COD/PLACED snapshots remain accepted for compatibility; no existing rows are rewritten. The new pending COD state cannot be changed to PAID/PLACED/PROCESSING through the generic Admin Order-status endpoint to bypass automatic confirmation.

## Requirement mapping

| Owner requirement | Implementation / model / authorization | Transaction and evidence |
| --- | --- | --- |
| Confirm unpaid COD only after attempt exists | Commerce customer_order/order_status_history; COD attempt ID/method/order/amount/status checked; trusted service credential | HTTP call after commit; new locked local confirmation transaction; checkout/recovery/cancellation-race/failed-ack tests |
| Preserve order when creation is unavailable | Existing Order ownership and deterministic initial key; Customer/Admin recovery both self-only | Saved PENDING_PAYMENT survives failures; recovery changes to PLACED once; no automatic uncertain POST replay |
| Admin alone records actual collection | Payment payment_attempt/financial_transaction; strict JSON; Admin JWT; recorded_by_user_id and receipt/time | Attempt row lock; local ledger/status/outbox transaction; concurrency and rollback tests |
| Confirmed does not mean paid | COD pending creation has no financial_transaction; no Order PAID transition on confirmation/collection | Real HTTP/PostgreSQL assertions plus both packaged JARs with actual checkout and signed JWTs |

## Actual verification

```powershell
mvn -o -B -ntp -f commerce-service/pom.xml '-Ddebug=false' '-DargLine=-Duser.timezone=UTC' verify
mvn -o -B -ntp -f payment-service/pom.xml '-Ddebug=false' '-DargLine=-Duser.timezone=UTC' verify
node scripts/verify-cod-two-step.cjs
node scripts/validate-cod-postman.cjs
node scripts/validate-us0402-postman.cjs
node scripts/validate-handoff-postman.cjs
node --check scripts/verify-cod-two-step.cjs
git diff --check
```

| Check | Actual result |
| --- | --- |
| Commerce regression/package | **53 passed; 0 failures, 0 errors, 0 skipped; BUILD SUCCESS** |
| Payment regression/package | **61 passed; 0 failures, 0 errors, 0 skipped; BUILD SUCCESS** |
| Actual Commerce/Payment JARs | **PASS, seven scenarios:** previous SePay settlement/PAID, lost ACK/retry, duplicate protection, cancelled-Order reconciliation; real COD checkout/obligation, Customer denial/Admin single collection, failed COD handoff/owner recovery |
| COD Postman JSON/templates/script syntax | **PASS:** eight requests/eight scripts; manual HTTP execution NOT performed |
| Existing SePay and handoff Postman structures | PASS, nine requests/fourteen scripts and eight requests/eight scripts respectively |
| Whitespace | git diff --check passed |

Commerce initially had one test-fixture error when overriding an existing Mockito answer; switching to doAnswer corrected the fixture. The final full 53-test regression was then rerun. No failing/skipped test is counted as a pass. Payment passed the full 61-test gate.

Evidence: `commerce-service/target/cod-two-step-verified.log`, `payment-service/target/cod-two-step-regression.log`, both modules' `target/surefire-reports`, and `target/cod-two-step-e2e/result.json` plus application logs. These are ignored local artifacts.

The two-JAR harness ran only after both regression gates passed. It creates a uniquely labelled PostgreSQL container with separate fresh Commerce/Payment databases and application logins, loopback ports and an ephemeral in-memory RSA key/JWKS fixture. Its Identity-address fixture validates the synthetic owner's credential. A controlled service bridge simulates unavailable Payment during one real checkout. No business repository is mocked in that two-JAR flow. Only the harness's own Java children, servers and exact labelled container are removed.

## Exact files changed for this extension

Production additions:

```text
commerce-service/src/main/java/com/stylemind/commerce/repository/order/CodOrderConfirmationRepository.java
commerce-service/src/main/java/com/stylemind/commerce/service/order/CodOrderConfirmationService.java
```

Production modifications:

```text
commerce-service/src/main/java/com/stylemind/commerce/service/order/OrderService.java
commerce-service/src/main/java/com/stylemind/commerce/service/order/OrderPaymentHandoff.java
commerce-service/src/main/java/com/stylemind/commerce/controller/order/OrderController.java
commerce-service/src/main/java/com/stylemind/commerce/controller/order/OrderPaymentController.java
payment-service/src/main/java/com/stylemind/payment/dto/OrderPaymentRequest.java
payment-service/src/main/java/com/stylemind/payment/dto/ConfirmCodCollectionRequest.java
payment-service/src/main/java/com/stylemind/payment/controller/PaymentAttemptController.java
payment-service/src/main/java/com/stylemind/payment/repository/PaymentAttemptRepository.java
payment-service/src/main/java/com/stylemind/payment/service/PaymentAttemptService.java
```

Tests, verification and handover:

```text
commerce-service/src/test/java/com/stylemind/commerce/BranchCartOrderPostgresIntegrationTests.java
commerce-service/src/test/java/com/stylemind/commerce/CommerceServiceApplicationTests.java
commerce-service/src/test/java/com/stylemind/commerce/OrderPaymentHandoffTests.java
commerce-service/src/test/java/com/stylemind/commerce/OrderPaymentRecoveryHttpTests.java
payment-service/src/test/java/com/stylemind/payment/OrderPaymentHttpTests.java
payment-service/src/test/java/com/stylemind/payment/PaymentWorkflowServiceTests.java
payment-service/src/test/java/com/stylemind/payment/PaymentRuntimePostgresIntegrationTests.java
scripts/verify-cod-two-step.cjs
scripts/validate-cod-postman.cjs
postman/StyleMind-COD-Two-Step.postman_collection.json
postman/COD_TWO_STEP_RUN_GUIDE.md
postman/README.md
postman/COMMERCE_PAYMENT_HANDOFF_RUN_GUIDE.md
docs/planning/COD_TWO_STEP_CONTRACT.md
docs/planning/COMMERCE_PAYMENT_HANDOFF_PROPOSAL.md
docs/governance/CURRENT_PROJECT_CONTEXT.md
docs/governance/CURRENT_BUSINESS_RULES.md
docs/governance/OPEN_IMPLEMENTATION_DECISIONS.md
docs/qa/COD_TWO_STEP_REPORT.md
```

## Environment and limits

- No persistent database, ignored .env, real credentials or live bank were changed/accessed. Only disposable database resources were used; no owner Compose stack was started/rebuilt. Existing migrations were not edited. This extension adds no migration and no cross-service database read/FK.
- Original staged changes remain untouched; the earlier US-04.01 worktree is unchanged. No Git reset/checkout/revert/clean/commit/push was performed. The backlog workbook is absent at its supplied path and was not edited.
- Admin collection is an attestation of physical cash receipt, not independent verification by the system. Order confirmation does not create a financial transaction; only successful authorized collection does.
- COD PaymentConfirmed remains in the existing local outbox; the US-04.02 dispatcher remains SePay-only. COD receipt propagation to Commerce, delivery guarantees after Commerce crashes, notification/email, best-effort cart redesign, manual reconciliation/refunds and paid-Order cancellation remain separate scope/TBD.
- Existing cart checkout is still transactional with Order creation. Existing generic SePay Admin status transitions are not redesigned by this COD-only extension. Do not infer completion of the whole broader section 7 from these two approved COD steps.
- Manual Postman execution and real cash/bank/provider delivery were not performed. Local regression and two-JAR evidence do not establish production readiness. [Run guide](../../postman/COD_TWO_STEP_RUN_GUIDE.md).

Follow-up: COD delivery is completed and reverified separately under [US-04.03](US-04.03_COD_COLLECTION_REPORT.md). Counts above describe the earlier two-step baseline.
