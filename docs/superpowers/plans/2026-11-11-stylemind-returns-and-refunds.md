# StyleMind Returns and Refunds Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans or superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Let signed-in customers request item-level returns and let shop staff record manual refunds for inspected returns or approved paid cancellations.

**Architecture:** Commerce owns return eligibility, evidence, inspection, and the cancellation refund obligation. Payment owns the protected bank details, the refund against the actual collection, and delivery of refund completion back to Commerce. The frontend renders persisted states from both services.

**Tech Stack:** Spring Boot, PostgreSQL, Flyway, React, TypeScript, Vitest.

**Spec:** The owner approved the return and refund flow in the conversation. [Cancellation design](../../planning/ORDER_CANCELLATION_REFUND_DESIGN_DRAFT.md) and [V13 scope](../../planning/PAID_QR_CANCELLATION_V13_SCOPE.md) provide the cancellation baseline. The owner approved the later Commerce return and Payment refund schema scopes. Commerce V14 and V15 now belong to merged stock and branch changes, so the return scope uses V16.

## Global constraints

- Eligibility lasts seven days from `customer_order.completed_at`. The shop reviews the pictures, may request more, then picks up and inspects the selected items. A failed inspection rejects the return.
- COD requires a recorded cash collection and VietQR requires a settled payment. Approved paid cancellations use their existing full-amount obligation. Return amounts use the order-item snapshot and selected quantities.
- The shop makes the transfer manually and pays its transfer fees. An admin records completion after making the transfer and supplies the transfer reference and supporting evidence reference.
- Late VietQR receipts remain available for reconciliation through the existing payment-exception flow.
- Preserve applied migrations and service database ownership. Test with disposable PostgreSQL data.

## Review focus

- Two requests for the same units cannot exceed the quantity on the order item.
- A different customer cannot view the return, evidence or refund by guessing its ID.
- More evidence is accepted only for a return awaiting pictures. A rejected inspection cannot open a refund.
- A repeated confirmation or overlapping refund cannot pay the same amount twice.
- A Commerce outage after the manual transfer is recorded leaves a durable Payment outbox entry that can be retried.

## Checkpoint 2026-11-11

- Commerce verification passed 29 tests on isolated PostgreSQL, including late VietQR, migration of an existing V13 refund obligation to V16, and the order-item SKU, color and size returned by the API. Payment Surefire reports 95 passing tests.
- Isolated HTTP smoke on the final Commerce jar and the Payment jar passed partial COD and VietQR returns and the full paid VietQR cancellation refund. Each verified Payment-to-Commerce synchronization. The Windows PostgreSQL smoke script is `C:/Users/TinTran/AppData/Local/Temp/stylemind-refund-native-e2e-20261111.cjs`.
- The FE shows the ordered variant, estimated refund, photo previews and admin transfer form. Seventeen affected tests, TypeScript and the production build passed. Edge rendered the customer layout at 390 and 1280 px and the admin layout at 1280 px with synthetic data.
- The full FE run passed 453 of 470 tests. Its 17 failing test names exactly match the saved pre-refund baseline.
- The shared local stack has not been rebuilt or migrated. The refund key setup and two-service rebuild command are in [the run guide](../../../postman/RETURNS_AND_REFUNDS_RUN_GUIDE.md).

## Task 1 Commerce return and refund sources

- [ ] Add integration coverage for order ownership, return cutoff, selected quantities, evidence and inspection transitions.
- [ ] Add Commerce V16 and return controllers/services for `/api/v1/orders/{orderId}/returns`, owner evidence, and the admin queue and decisions.
- [ ] Implement the internal `GET /internal/v1/refund-sources/{sourceType}/{sourceId}` snapshot and `POST /internal/v1/refund-results` completion. Use the existing service-token guard.
- [ ] Verify both `RETURN` and `CANCELLATION` sources and exact replay behavior on PostgreSQL.

## Task 2 Payment refund and delivery

- [ ] Add integration coverage for one source per refund, the actual-collection cap, authorization and repeated completion.
- [ ] Add Payment V3, protected destination details and customer/admin API actions on `/api/v1/refunds` and `/api/v1/admin/refunds`.
- [ ] Write the completion event in the same transaction as the manual record. Retry delivery to Commerce using the service-token contract.
- [ ] Verify encryption, terminal state, amount cap, delivery and restart/retry behavior.

## Task 3 Frontend customer and admin

- [ ] Exercise the customer flow from the completed-order page and admin flow from the live request queue.
- [ ] Connect the form for selected items, reason and pictures, the return timeline and additional evidence, and refund destination details.
- [ ] Connect admin review, final inspection and manual transfer recording for return and cancellation sources.
- [ ] Run affected Vitest suites and TypeScript checks.

## Task 4 Configuration and handoff

- [ ] Wire the refund encryption key into Compose and document local setup.
- [ ] Update Postman and the run guide with customer/admin flows, test fixtures, negative cases, and service restart commands.
- [ ] Validate the artifacts and report tests and any runtime work that remains.
