# COD order confirmation and collection

Owner authorization: explicit request to implement these two separate steps: automatically confirm a COD Order after a pending payment attempt exists; only an Admin recording actual collection creates a financial transaction and marks the payment paid.

The owner explicitly chose the existing-schema mapping in the follow-up reply: new COD Orders start at PENDING_PAYMENT while attempt creation is unacknowledged, then become PLACED (confirmed for fulfilment, without asserting collection). No CONFIRMED enum/schema migration is added. Payment remains PENDING until collection. Payment's stored SETTLED is exposed as PAID in its existing API. Order fulfilment state and payment state are separate concepts.

## Order confirmation

- Commerce commits its Order, address/items and existing cart checkout operation first. It then calls Payment outside its transaction using the existing service credential and deterministic initial-Order key.
- Payment accepts COD/PENDING_PAYMENT snapshots as well as legacy COD/PLACED snapshots. Creating/replaying an attempt alone writes no financial transaction/outbox confirmation. COD has no bank QR or online expiry.
- After a valid matching COD attempt acknowledgement, Commerce starts a new local transaction, locks/revalidates the Order and changes only PENDING_PAYMENT to PLACED with one history entry. A later PAID replay also proves that the existing attempt was created; it cannot create or reverse financial truth in Commerce.
- Payment failure, invalid acknowledgement or confirmation-write failure keeps a committed COD Order pending. The existing self-only Customer/Admin recovery endpoint retries the same initial key, confirms once and cannot reopen a cancelled Order. No new user-supplied amount/customer field is accepted.
- Checkout rereads COD state after the synchronous AFTER_COMMIT handoff, so its response reflects PLACED on success and PENDING_PAYMENT on failure. Legacy confirmed/advanced COD Orders are never reset by recovery.
- Admin Order status updates cannot bypass automatic confirmation or mark a pending COD Order PAID. Existing cancellation and subsequent fulfilment rules remain unchanged.

## Collection

- Only the existing Admin-authorized POST /api/v1/admin/cod-collections records receipt. Calling it is the Admin's attestation that cash has actually been collected; creating an Order or clicking a Customer action cannot settle a payment.
- Require a strict attempt UUID and positive whole JSON integer amount matching the pending COD obligation. Optional receipt reference is bounded; a supplied collection time must be valid and not in the future. No scalar/string rounding or client-supplied status is accepted.
- Lock the attempt and atomically create one financial transaction, mark the attempt SETTLED and write the existing local outbox event. Store the Admin ID/receipt/time. Wrong method/amount, missing attempt, non-Admin, repeated/concurrent collection and failed persistence cannot create duplicate credit. Repeat returns the existing 409 contract.
- Do not add a SHIPPED-only prerequisite. Do not change Order fulfilment to PAID on COD collection. COD event delivery into Commerce remains outside this two-step change; the earlier US-04.02 sender is SePay-only.

No new migration, paid-Order cancellation/refund policy, notification integration, best-effort cart redesign or manual exception-resolution workflow is introduced. Those parts of the broader business description remain separate scope/TBD. Existing persistent data and ignored environment files are not changed.

US-04.03 follow-up: the owner has now approved COD confirmation transport and V8. The earlier transport exclusion above is superseded by [the scoped US-04.03 contract](US-04.03_COD_COLLECTION_CONTRACT.md); COD fulfilment remains unchanged.
