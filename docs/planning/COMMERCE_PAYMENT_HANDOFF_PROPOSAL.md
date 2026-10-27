# Commerce to Payment handoff

Status: APPROVED by the owner's explicit acceptance in this conversation on 2026-10-21. Originally prepared on 2026-10-20. Approval is scoped to this handoff, including retained Orders on failure, explicit recovery and internal service authentication.

## Observed baseline

- Commerce checkout creates an Order and returns its existing OrderResponse. It calls Identity for the shipping address; it does not call Payment.
- COD starts as PLACED; SEPAY_VIETQR starts as PENDING_PAYMENT.
- Payment exposes POST /api/v1/payments/attempts. That path obtains an authoritative snapshot through CommerceOrderClient.
- RestCommerceOrderClient expects GET /internal/v1/orders/{orderId}/payment-snapshot, which Commerce does not implement.
- Payment currently has uncommitted implementation changes. Preserve them when adding the handoff.
- The governance documents do not contain an approved handoff/failure contract. The specified backlog workbook is absent at its configured path in this workspace.

## Owner-approved contract

The owner accepted the following option. Implementation and verification evidence are recorded separately; approval does not imply that local tests have passed.

1. After the Commerce order transaction commits, Commerce sends the committed order snapshot to Payment for both COD and SEPAY_VIETQR. Never make this call before commit or while holding a Commerce row lock. A rollback sends nothing.
2. Add POST /internal/v1/payments/order-attempts to Payment. Keep the existing public Payment request/response contract. The internal path uses Commerce's snapshot directly rather than calling the currently missing snapshot endpoint.
3. The internal JSON contains order_id, customer_id, payment_method, accepted_total_vnd, order_status and request_key. Values come exclusively from the committed Commerce order, never from client-supplied customer IDs or amounts. Use a deterministic request_key derived from the order ID for this initial attempt.
4. Authenticate this internal path with X-StyleMind-Service-Token from external environment configuration, dedicated to this integration. Reject missing/incorrect credentials; refuse requests when the configured secret is absent. Do not commit, log or return the token. Do not forward the customer's JWT to this path. The service credential authorizes Commerce's customer_id assertion under this approved trust contract.
5. Validate UUIDs, allowed methods/statuses, required fields and a positive VND amount. Accept initial COD/PLACED and SEPAY_VIETQR/PENDING_PAYMENT snapshots only. Reject unknown fields. Payment creates an attempt in its own transaction using its existing payment_attempt model, QR generation and attempt TTL.
6. Successful creation returns 201 with the existing PaymentAttemptResponse. Repeating the same order/request_key and snapshot returns the existing attempt; conflicting snapshot values return 409. Serialize concurrent initial requests for an order using isolated Payment-side transaction locking so only one initial attempt is created. No new migration is proposed.
7. If Payment fails or times out, retain the committed Order and the existing successful checkout response. Do not roll back or delete the Order and do not report it as paid. Do not automatically retry uncertain requests. This option does not provide durable or guaranteed delivery after a Commerce process crash.
8. Add POST /api/v1/orders/{orderId}/payment-attempt for explicit recovery and QR retrieval. Require Commerce's existing CUSTOMER/ADMIN JWT rules, with both roles restricted to their own order by JWT sub. Foreign and missing orders return the same 404. Reload the committed order and submit the same request_key. Restrict this recovery to the initial payable states above. Relay a safe PaymentAttemptResponse on success; use safe upstream-unavailable/unknown errors on failure. Do not allow an arbitrary destination URL or snapshot in the request body.
9. Do not change the existing checkout response fields, Order state transitions, paid-order cancellation, payment exceptions, refunds or PaymentConfirmed delivery. The existing public Payment API's missing snapshot integration is a separate unresolved issue and must be called out in the run guide.

## Requirement and verification mapping

| Requirement | Ownership/attributes | Endpoint/validation | Transaction and evidence required |
| --- | --- | --- | --- |
| Send committed order | Commerce order_id, customer_id, payment_method, accepted_total_vnd, order_status | Internal POST, server-derived payload | Unit and integration: commit sends once; rollback sends nothing; no active Commerce transaction during HTTP |
| Authenticate sender | Commerce-to-Payment credential, external environment only | Internal credential required; malformed/unknown fields rejected | HTTP tests: missing/wrong/unconfigured token rejected; no secrets in error payloads |
| Create initial attempt | Payment payment_attempt; COD or SEPAY_VIETQR | Existing response shape; positive amount and initial status validation | Repository tests with disposable PostgreSQL: same-key replay, mismatched replay, concurrent duplicate requests and rollback |
| Preserve checkout on failure | Committed Commerce order and existing response | No checkout contract change | Timeout/refusal/5xx tests: order retained, unpaid state retained; no success claim about Payment |
| Explicit recovery | JWT sub must own Commerce order | New recovery POST; owner A/B, missing/foreign order and invalid UUID tests | Unit/HTTP/integration: deterministic replay, unchanged order, safe errors and QR response |

The owner explicitly approved the business behavior, API additions and service authentication above under AGENTS.md. Existing unrelated implementation remains evidence only, not approval.

## Delivery requirements

- Add implementation, automated negative/authorization/transaction/concurrency tests and an importable Postman collection with a run guide.
- Run available existing Commerce and Payment regression gates before any optional smoke test; report skipped integration tests separately from passes.
- Use disposable integration resources only. Do not start Compose or touch persistent databases.
- Report exact changed files, commands/results, runtime HTTP execution separately from Postman structural validation, and remaining limitations.

Remaining decisions: guaranteed delivery and fresh attempts after expiry remain TBD and are not authorized by this contract. Snapshot lookup for the pre-existing public Payment API and consumption of PaymentConfirmed are outside this integration scope.
