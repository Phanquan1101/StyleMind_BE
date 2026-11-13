# Order, cancellation, return and refund alignment

## Intent and authority

This records the owner's decisions in the current conversation, after the 2026-11-13 pull. It supersedes older draft rules that permit a second cancellation or return request. Implement by extending the existing Commerce, Payment and FE flows, not by creating parallel workflows. The user runs Git commands. Current reported BE and FE working trees are clean.

## Required behavior

1. COD stock is deducted when the order is created. VietQR reserves stock while the QR checkout is active; verified payment creates exactly one paid order. Cancellation before delivery restores stock exactly once. A return never automatically restores stock.
2. An unpaid VietQR checkout is not an order. The customer can cancel it and start another checkout. A pending unpaid checkout must not block another distinct checkout. A payment arriving after cancellation or expiry remains recorded for reconciliation and must not create a false order or silently disappear.
3. A customer gets at most one cancellation request per order for its lifetime, including after rejection. Admin must resolve a pending cancellation request before confirming or advancing the order. Unconfirmed unpaid COD orders may be canceled directly; a confirmed order or paid online order uses admin review. No cancellation while shipping. An approved paid cancellation creates a full manual refund obligation; approval alone is not proof of bank transfer.
4. A customer gets at most one return request per order for its lifetime, including after rejection. The customer selects items and quantities in that request. Submit within seven days from successful delivery. Admin may request more photos on the same pending request; only then may the customer supplement evidence. Shop collects the returned goods; admin checks them and may reject at the final check. A successful return refund covers the approved item amount. Shop bears the bank transfer fee. Rejected return requests cannot be submitted again.
5. COD, VietQR and VNPay follow the same cancellation and return eligibility rules where their financial states permit. COD delivery is one admin action, “Giao thành công và thu COD”; the customer sees “Đã giao hàng”. Backend payment collection must remain idempotent and recover after an interrupted cross-service call. VNPay payment confirmation comes only from a verified IPN, never the browser return.
6. Admin manually transfers a refund to the customer's encrypted bank destination. The completion UI requires the receipt photo and an explicit confirmation that money was actually transferred, without asking the admin for transaction/reference codes. Backend records an auditable internal completion identifier, prevents duplicate or excess refund, stores the private receipt and makes it visible to the customer after completion. Do not fabricate a bank transaction reference.
7. Admin Orders is the working area for order, cancellation, return, refund, and payment-exception actions. Actionable counts and request-first status labels let admin see work needing attention. Preserve the underlying fulfillment, payment and refund states in details and preserve existing deep links. New customer/admin UI must reflect confirmed backend states and never show payment success prematurely.

## Deferred

Delivery failure, refusal to receive goods, and its stock/payment consequences are intentionally deferred by the owner. Do not build that branch now.

## Design choices for this implementation

- Prefer existing order-row locks and history for lifetime-request enforcement, preserving historical duplicate requests. A new unique constraint would conflict with existing history and would need a separate approved migration scope.
- Keep existing SePay checkout and VNPay IPN contracts. Extend existing payment/refund source validation to VNPay rather than duplicating the refund system.
- For COD synchronization, prefer an idempotent recovery path based on persisted order/payment state if it can guarantee eventual completion. If it cannot, prepare a concrete migration scope for owner approval before adding an outbox table.
- Add an atomic receipt-backed completion operation using existing Payment V3/V4 data. Keep old API compatibility for existing clients while new FE uses the receipt-backed operation.

## Acceptance and risk focus

- Concurrent duplicate cancellation/return submissions, rejected history, and retry of the same request key.
- Cancellation competing with fulfillment; payment competing with QR cancellation/expiry; delayed provider callback and event delivery.
- COD delivery succeeding while Payment is unavailable, followed by retry/restart without a second financial transaction.
- VNPay paid cancellation and return require a verified matching Payment event and settled ledger; wrong amount, owner or method cannot unlock refund.
- Receipt upload/completion rollback, replay, access isolation, size/type validation, and capped total refunds.
- FE active queue counts, status priority, deep links, page reload and no fake success.

Only disposable integration databases and local tests are in scope. No Compose restart or persistent database mutation is authorized by this document.
