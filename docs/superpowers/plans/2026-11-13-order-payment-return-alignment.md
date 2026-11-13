# Order, Payment, Cancellation, Return and Refund Implementation Plan

> **For agentic workers:** Use `superpowers:subagent-driven-development` task by task. Owner runs Git commands; workers must not run Git or commit. Each task receives a fresh focused implementer, focused review, and test evidence.

**Goal:** Align the existing COD, VietQR and VNPay paths with the owner's cancellation, return, refund and admin workflow decisions.

**Architecture:** Commerce owns orders, quantities and request eligibility; Payment owns collection, verified settlement and manual refund ledger; FE presents only backend-confirmed state. Reuse the existing V11–V17 Commerce and V3–V5 Payment models. No cross-service database access.

**Tech Stack:** Java/Spring Boot, Spring Data JPA and existing JDBC repositories, PostgreSQL/Testcontainers, React/TypeScript/Vitest.

**Spec:** `docs/superpowers/plans/2026-11-13-order-payment-return-alignment-spec.md`

## Global Constraints

- Preserve existing versioned migrations and historical rows. No new schema without a concrete owner-approved scope.
- Never run Git commands; the owner runs them. Preserve all changes already in both working trees.
- No Compose restart, real payment call or persistent database mutation during implementation.
- No delivery-failure branch. No fake bank reference or payment success.
- Extend only Commerce, Payment and FE paths relevant to these rules. Keep old public contracts compatible where practical.
- For changed HTTP APIs, update Postman collection and run guide. Tests must include owner isolation and negative input cases.

## Review Focus

1. Concurrent request creation after a prior rejection still yields only one lifetime cancellation/return request; identical-key replay is harmless.
2. Pending cancellation blocks confirmation, processing and shipment even when an admin has an old page open.
3. Payment event arriving after QR cancel/expiry never creates an order; a distinct checkout remains possible.
4. COD delivery succeeds while Payment is unavailable, then recovery settles once after restart; customer and admin see distinct delivery/payment progress.
5. VNPay mismatch/duplicate IPN cannot unlock refund, and receipt-backed refund retry cannot double-pay or substitute another case's photo.

---

### Task 1: Commerce request lifetime and fulfillment guard

**Files:** `commerce-service/src/main/java/com/stylemind/commerce/service/order/impl/OrderServiceImpl.java`, `repository/order/CancellationRepository.java`, `service/returns/impl/ReturnServiceImpl.java`, `repository/returns/ReturnRepository.java`, applicable response DTO; tests in `CartOrderPostgresIntegrationTests.java`, `OrderConfirmationMigrationPostgresTests.java`, `ReturnPostgresIntegrationTests.java`.

**Interface:** Existing cancellation and return endpoints remain. Add a named eligibility reason for an already-used return request; cancellation continues to use the existing conflict error. Ensure all creators and admin transitions serialize on the order row.

- [ ] Add failing isolated tests for rejected-request retry, same-key replay, concurrent request creation and pending-cancellation confirm/process/ship attempts.
- [ ] Run focused tests and observe the intended failures.
- [ ] Enforce one lifetime request of each type using existing locked order and request history; preserve historical rows. Block admin fulfillment until cancellation is decided. Keep evidence supplementation limited to `MORE_IMAGES_REQUESTED`.
- [ ] Run focused migration/integration and authorization tests; update Postman guide and collection for changed errors.
- [ ] Review task diff by reading changed files and targeted tests; do not use Git.

### Task 2: Commerce VNPay cancellation, return and refund sources

**Files:** `OrderServiceImpl.java`, `repository/order/CancellationRefundRepository.java`, `repository/returns/ReturnRepository.java`, `repository/returns/RefundSourceRepository.java`, `CartOrderPostgresIntegrationTests.java`, `ReturnPostgresIntegrationTests.java`, HTTP/Postman documentation.

**Interface:** Existing `CANCELLATION` and `RETURN` refund source endpoints must resolve a matching verified VNPay payment event exactly as they do for a matching SePay event; Payment receives the existing source type/id/amount contract.

- [ ] Add failing paid-VNPay cancellation and delivered-VNPay return tests, plus wrong owner/method/amount/unapplied-event cases.
- [ ] Run focused tests and observe the intended failures.
- [ ] Extend verified online-payment predicates to VNPay without weakening the event, owner, amount and terminal-state checks. Preserve COD and SePay behavior.
- [ ] Run focused integration/HTTP tests and update the Postman cases.
- [ ] Review the cross-service source contract with Task 3 before marking complete.

### Task 3: Payment refund completion with one receipt photo

**Files:** `payment-service/src/main/java/com/stylemind/payment/controller/RefundController.java`, `dto/RefundRequests.java`, `service/RefundService.java`, `service/impl/RefundServiceImpl.java`, refund receipt repository/storage and relevant tests in `RefundWorkflowPostgresTests.java`; Payment Postman collection and guide.

**Interface:** Add one admin-only multipart completion endpoint taking a validated JPEG/PNG image and case/source identity. It completes a PROCESSING case only after storing the immutable receipt and recording an internal completion identifier, audit and outbox in one transaction. Old completion endpoint remains for existing clients. FE Task 6 uses the new endpoint.

- [ ] Add failing tests for image-only completion, missing/oversized/wrong-type photo, other-admin access, atomic rollback, identical retry and different-photo conflict.
- [ ] Run focused tests and observe the intended failures.
- [ ] Reuse existing Payment V3/V4 tables, case lock and allocation cap. Do not present the internal identifier as a real bank transaction code.
- [ ] Run focused tests; update Postman and privacy guidance.
- [ ] Review authorization, duplicate completion and receipt access.

### Task 4: Reliable COD delivery and collection

**Files:** Commerce order status/controller/payment client and existing Payment COD collection endpoint/service, plus their integration tests. Exact file list and transport will be frozen after Task 1/2 and the focused architecture check.

**Interface:** One admin command means delivered and COD collected. A transient Payment outage leaves a durable recoverable state, not a second admin action. Collection is idempotent and order/payment status remain distinguishable until synchronization finishes.

- [ ] Add failing test for delivery success with Payment temporarily unavailable, recovery after restart and concurrent delivery calls.
- [ ] Implement a durable recovery mechanism from persisted order/payment state without new schema if the architecture check proves it safe; otherwise document a concrete migration scope and obtain owner approval before adding a table.
- [ ] Reuse Payment's delivered-COD idempotent collection and PaymentConfirmed outbox. Preserve audit and no double collection.
- [ ] Run Commerce/Payment integration tests and targeted FE regression.
- [ ] Review security of the internal call and pending-sync visibility.

### Task 5: Customer FE eligibility and checkout status

**Files:** `src/features/orders/components/OrderDetailSections.tsx`, `src/features/returns/CustomerReturns.tsx`, `src/features/orders/pages/AccountOrdersPage.tsx`, `src/features/checkout/pages/QrCheckoutPage.tsx` and focused tests.

**Interface:** Consume the existing Commerce eligibility/history and QR checkout APIs. A rejected request remains visible but cannot be resubmitted; evidence can be added only when requested. Open QR is labeled as a checkout, not an order.

- [ ] Add failing component tests for rejected cancellation/return, fresh checkout after unpaid cancellation and accurate open/cancelled/expired labels.
- [ ] Run focused tests and observe intended failures.
- [ ] Update controls and copy without making FE the sole business-rule gate.
- [ ] Run focused tests, typecheck and build.
- [ ] Review customer-visible state against BE responses.

### Task 6: Admin FE unified Orders workspace

**Files:** `src/features/admin/components/AdminShell.tsx`, `AdminOrdersPanel.tsx`, `AdminRequestsPanel.tsx`, `AdminReturnsPanel.tsx`, `src/features/admin/pages/AdminPage.tsx`, `src/features/returns/RefundPanel.tsx`, `src/features/returns/returnsApi.ts` and focused tests.

**Interface:** Orders navigation hosts fulfillment, cancellations, returns, refunds and payment exceptions. Existing deep links redirect or resolve to the right Orders subview. Show actionable counts and prioritize pending cancellation/return/refund labels in list rows while retaining actual order/payment status in detail. Admin uploads one receipt photo and confirms completed manual transfer through Task 3 API.

- [ ] Add failing tests for queue badges, request-priority labels, deep links, VNPay label/filter and photo-only refund submission.
- [ ] Run focused tests and observe intended failures.
- [ ] Implement shared status projection and Orders subviews; remove separate Payment/Requests sidebar entries while preserving relevant functions.
- [ ] Wire Task 3 receipt-backed completion, pending/complete/retry states, and existing cancellation/return actions.
- [ ] Run focused tests, FE typecheck and build; review keyboard, narrow viewport and errors.

### Task 7: Cross-flow verification and handover

Implementation and local verification are recorded in `docs/qa/ORDER_PAYMENT_RETURN_ALIGNMENT_2026-11-13.md`. Tasks 1–6 have been implemented. Local gates and the existing failing Payment test classes are documented in that report. Bank callbacks, browser acceptance and a combined running-service smoke remain for runtime verification.

**Files:** targeted BE and FE tests/docs only as needed after Tasks 1–6.

- [ ] Run affected Commerce, Payment and FE tests including COD, VietQR, VNPay, cancellation, return and refund regressions. Report any skipped Docker-dependent tests as skipped.
- [ ] Verify Postman JSON structural validity; distinguish this from live API execution.
- [ ] Read changed files for overlapping edits and verify no old migration was modified.
- [ ] Produce a concise handover: exact files, commands/results, remaining risks, environment touched and user-run Git next steps.
