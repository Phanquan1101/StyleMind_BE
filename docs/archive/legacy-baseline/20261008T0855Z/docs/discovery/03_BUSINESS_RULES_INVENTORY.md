# StyleMind Business Rules Inventory

This is the canonical BR register. Sources and classification meanings are in [Project Facts](01_PROJECT_FACTS.md). A TBD record identifies an unresolved rule, not an enforceable invariant. Confirmed rules retain their conditions; numeric limits, policy choices and transaction mechanisms stay open. Security and architecture constraints are included where the original business description explicitly treats them as invariants.

| ID | Statement | Applies to | Source | Classification |
| --- | --- | --- | --- | --- |
| BR-001 | A real account is created only after a correct, unexpired registration OTP is verified. | Registration; FR-001 | S01 P022-030 | CONFIRMED |
| BR-002 | Registration checks that the email does not already exist. Concurrent duplicate handling and email normalization are TBD. | Identity; FR-001 | S01 P022 | CONFIRMED |
| BR-003 | Passwords and OTP are not stored as plaintext; OTP has expiry, attempt limits and resend cooldown. | Registration/reset; SEC-001, SEC-002 | S01 P024, P031-034, P061-063 | CONFIRMED |
| BR-004 | A reset token, if the reset-token alternative is used, cannot be reused after completion. | Password reset; FR-003 | S01 P057, P064 | CONFIRMED |
| BR-005 | An address belongs to the current user; another user's address cannot be operated on. Checkout address must be valid. | Customer/address; FR-005, FR-016 | S01 P092-097 | CONFIRMED |
| BR-006 | Whether one default address is an enforced maximum, a required exactly-one state, or only a recommendation is TBD. | Address default/delete; FR-005 | S01 P094 says one user should have one default | TBD |
| BR-007 | Final rules for active-only public visibility, activation without variants, last-variant deletion, initial inactive status and category deletion are TBD. | Catalog/Product/Variant; FR-008 through FR-010 | S01 P114, P126, P140-142 use should/may | TBD |
| BR-008 | Cart/order do not trust frontend prices. Cart price is display information; checkout obtains price again from product data. | Cart/checkout; FR-011, FR-014, FR-016 | S01 P154-156, P191, P208, P224 | CONFIRMED |
| BR-009 | A user has one active cart, and only its owner can view/edit it. Guest cart has no userId and is identified by the client session id. | Cart; FR-012, FR-013 | S01 P166-177 | CONFIRMED |
| BR-010 | On cart add, validate the variant; product and variant must be active, the variant in stock and quantity valid. | Cart add; FR-014 | S01 P186-190 | CONFIRMED |
| BR-011 | Guest-to-user merge is atomic: duplicate quantities combine; any invalid, unavailable or over-stock conflict fails the complete merge; price changes do not invalidate merge but must be shown; the guest cart clears or retires only after success. | Merge; FR-015 | S01 P197-199; DEC-030 | CONFIRMED; partial merge is out of scope for V1 |
| BR-012 | Checkout requires login, nonempty cart, valid address, active products/variants, stock and refreshed prices. | Checkout; FR-016 | S01 P203-208 | CONFIRMED |
| BR-013 | Repeating successful checkout with the same user's idempotency key must not create duplicate orders. Header mandatory status, lifetime and conflicting-payload behavior are TBD. | Checkout retry; FR-016 | S01 P209, P226, P510, P514 | CONFIRMED |
| BR-014 | Order items retain price_at_purchase, and orders retain purchase-time address/recipient snapshots despite later address edits. | Orders; DATA-002 | S01 P099-102, P219, P223-225 | CONFIRMED |
| BR-015 | Backend-calculated order totals are authoritative; payment reconciliation uses the amount created by the backend. V1 uses whole VND, shipping fee is 0 VND, no separate application tax/VAT line is added and promotions are out of scope. | Checkout/payment | S01 P228-236; DEC-032 | CONFIRMED |
| BR-016 | COD creates a pending payment transaction and may move the order through the approved lifecycle; the Payment is not financially PAID until collection is confirmed and no bank webhook is required. | COD; FR-017 | S01 P243-250; DEC-033 | CONFIRMED |
| BR-017 | Failure to clear cart or send notification during COD processing does not roll back the order. | COD checkout | S01 P246-252 | CONFIRMED |
| BR-018 | Frontend cannot confirm online payment; only authoritative payment confirmation can mark the payment paid. | SePay; FR-018, FR-019 | S01 P265-266; DEC-033 | CONFIRMED |
| BR-019 | Payment reconciliation requires valid matching confirmation and exact amount. A match marks the transaction PAID; underpayment, overpayment and unmatched payment do not. | SePay webhook | S01 P267-268, P273-278; DEC-033 | CONFIRMED |
| BR-020 | A duplicate payment webhook must not cause repeated processing, credit or confirmation; the source deduplication key is provider plus gateway transaction id. | Payment retries | S01 P269, P275, P511, P515 | CONFIRMED |
| BR-021 | Every order state change is checked against the explicit legal transitions below; an invalid transition produces a business error. | Order; FR-020 | S01 P294-306 | CONFIRMED |
| BR-022 | COMPLETED, CANCELLED, EXPIRED and FAILED are terminal order states and cannot transition onward; later payment must not revive a terminal order. | Order/payment races | S01 P301-303; DEC-033 | CONFIRMED |
| BR-023 | Customer cancellation is allowed only in PENDING, PAYMENT_PENDING, PAID or CONFIRMED; it is whole-order only in V1, requires a reason, and paid cancellation requires review with refund eligibility if approved. | Customer cancellation; FR-023 | S01 P311-319; DEC-034 | CONFIRMED |
| BR-024 | Admin cancellation requires an appropriate reason, corresponding refund/payment-cancellation handling when a payment exists, and valid state transitions. | Admin cancellation; FR-024 | S01 P327-329 | CONFIRMED |
| BR-025 | Receipt image upload is restricted to the order owner and a suitable state, with image format and size validation. Exact states and limits are TBD. | Receipt images; FR-025 | S01 P337-342 | CONFIRMED |
| BR-026 | Return eligibility checks ownership and remaining quantity, and in V1 requires the Order to be COMPLETED within seven calendar days of completion. | Return; FR-026 | S01 P347-351; DEC-034 | CONFIRMED |
| BR-027 | Returned quantity cannot exceed purchased/remaining quantity; item-level, partial and full returns are supported. Repeated/concurrent request accounting remains TBD. | Return items; FR-027 | S01 P351, P361, P363; DEC-034 | CONFIRMED |
| BR-028 | Return reasons must be valid; admin review and QC are required before refund acceptance; rejection needs a reason; exchange/replacement is out of scope for V1. Detailed QC/restock criteria remain TBD. | Return review; FR-027, FR-028 | S01 P362, P372-374; DEC-034 | CONFIRMED |
| BR-029 | Shipment details may be submitted only for a suitable return request state; detailed state set and carrier integration remain TBD. | Return shipment; FR-029 | S01 P381; DEC-034 | CONFIRMED |
| BR-030 | Refund cannot exceed the amount actually paid for eligible item quantities; partial/cumulative basis, manual/admin processing, states and retry direction follow DEC-032 and DEC-034. | Refund; FR-030 | S01 P392-394; DEC-032, DEC-034 | CONFIRMED |
| BR-031 | A verified payout/refund destination belongs to its corresponding return request; detailed access and change rules remain TBD. | Return/refund payout; FR-031 | S01 P395; DEC-034 | CONFIRMED |
| BR-032 | AI purchasable recommendations must be connected to real StyleMind product/catalog data; invented purchasable products are prohibited. | AI Stylist; AI-003 | S03 section 11 | CONFIRMED |
| BR-033 | Height, bust, waist and hip measurements, if collected, are optional personalization; bust/waist/hip cannot be mandatory registration fields. | Body profile/registration; AI-013, DATA-004 | S03 section 5; S05 sections 2, 7C | CONFIRMED |
| BR-034 | An outfit respects a budget supplied by the user. What the budget covers and how unsatisfiable constraints are reported remain TBD. | AI recommendation; AI-004 | S01 P463 | CONFIRMED |
| BR-035 | New fashion knowledge cannot enter production before approval; ingestion creates a draft followed by admin review. | Knowledge ingestion; AI-008 | S01 P480-485 | CONFIRMED |
| BR-036 | Deleting an approved knowledge source requires related graph/vector-index updates. | Knowledge deletion; AI-008 | S01 P483, P487 | CONFIRMED |
| BR-037 | Services do not access each other's databases directly and do not have cross-service foreign keys. | Architecture/data ownership; ARCH-005 | S01 P489, P499-500 | CONFIRMED |
| BR-038 | Internal API calls require an internal token and are restricted to service-to-service use. | Internal integrations; SEC-008 | S01 P072, P501 | CONFIRMED |
| BR-039 | Retry handles failed work without repeating business work already completed successfully. | Checkout/webhook/notification retry; NFR-001 | S01 P503-516 | CONFIRMED |
| BR-040 | Notification failure does not roll back primary order/payment processing; delivery success/failure is recorded and disabled/missing mail configuration has fallback logs. | Notification; FR-033, NFR-002 | S01 P418-420 | CONFIRMED |
| BR-041 | Chat sessions are attached to a user or user context, and messages are stored to maintain consultation history. | AI chat; AI-001 | S01 P447-448 | CONFIRMED |
| BR-042 | Recommendation reasons must be explainable through style/body/occasion/rule, and returned product information must support viewing/buying. | AI response; AI-005, AI-006 | S01 P464-465 | CONFIRMED |

## Source order state machine

This table reproduces S01 P284-301. It is not a new state machine. Exact triggers, authorization, side effects, concurrency and payment/return mappings require OPEN-018 through OPEN-026. Multiple destinations are alternatives, not simultaneous transitions.

| Order state | Meaning in S01 | Allowed next states |
| --- | --- | --- |
| PENDING | Newly created; awaiting confirmation or payment processing | PAYMENT_PENDING, CONFIRMED, CANCELLED |
| PAYMENT_PENDING | Waiting for online payment | PAID, EXPIRED, FAILED, CANCELLED |
| PAID | Paid | CONFIRMED, PROCESSING, CANCELLED |
| CONFIRMED | Confirmed | PROCESSING, CANCELLED |
| PROCESSING | Being processed | SHIPPED, CANCELLED |
| SHIPPED | Being delivered | COMPLETED |
| COMPLETED | Completed | None; terminal |
| CANCELLED | Cancelled | None; terminal |
| EXPIRED | Payment waiting period expired | None; terminal |
| FAILED | Payment or processing failed | None; terminal |

Order PAID and payment-transaction PAID are distinct state-bearing concepts in S01. Their identical label does not establish one shared state machine. No payment, cancellation-request, return, QC, refund or notification state machine is supplied in full.

## Explicitly unresolved policy details

Source expressions such as category deletion should be avoided, tax if enabled, wrong amount may fail, cancellation may need review, and refund should meet eligibility are not converted into unqualified invariants. Their resolution is captured in OPEN-008, OPEN-012, OPEN-017, OPEN-021 and OPEN-026. No stock reservation rule, return-window value, refund formula, timeout value, key retention period or admin-guard algorithm has been invented.
