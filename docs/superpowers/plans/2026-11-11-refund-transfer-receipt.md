# Refund receipt and order status implementation plan

Spec: `docs/planning/REFUND_TRANSFER_RECEIPT_V4_SCOPE.md`, owner approved 2026-11-11. Follow-up: show confirmed payment and refund state on order lists without changing fulfillment states.

Execute inline in current BE/FE checkouts. Owner runs Git. Preserve existing AdminOrdersPanel layout changes. No persistent DB writes during verification.

- [x] Add failing PostgreSQL HTTP tests for receipt access/upload/replay, then implement Payment V4, JPA entity/repository, validator, existing RefundService/controller extensions and `has_receipt`.
- [x] Add failing FE tests for upload/view, then add authenticated receipt API and a small RefundReceipt component mounted in RefundPanel. Keep existing completion controls and bank access policy.
- [x] Add failing tests for refunded order lists and persistent COD collection status; expose `payment_status`, `refunded_amount_vnd`, `refund_status` from existing Commerce receipt tables and display in customer/admin lists. No new fulfillment state or migration.
- [x] Run affected Payment/Commerce PostgreSQL and FE suites, typecheck/build; inspect code for ownership, immutable proof, retry and object URL cleanup.
- [x] Update Postman collection/guide and record verification; owner rebuilds services and does live test.

Ruling: no agents/worktree/commits because owner retains Git control and requests inline work. Receipt is optional and is never a substitute for actual bank confirmation. API `PAID` is the existing mapped value of Payment SETTLED.

Verification: Payment 17 tests (RefundWorkflowPostgresTests 9, RefundDestinationCipherTests 2, PaymentCreationHttpPostgresTests 5, GuestPaymentMigrationPostgresTests 1), Commerce 68 tests (Return 8, CartOrder 57, RecoveryHTTP 3), FE 23 tests / 6 suites. No failures or skips in these selected suites. Typecheck and Vite build passed; existing bundle size warning remains. Postman JSON and 29 scripts parsed. DB migration verified from populated V3 to V4 only on isolated PostgreSQL. Browser/live financial transaction tests left to owner.
