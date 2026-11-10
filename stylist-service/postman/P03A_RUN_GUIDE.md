# P03-A Postman run guide

This collection covers the two implemented P03-A routes through the existing Gateway. It does not run request admission, generation, or recommendation.

## Import and configure

1. Import `StyleMind-Stylist-P03A.postman_collection.json` and `StyleMind-Stylist-P03A.postman_environment.json` into Postman, then select the environment.
2. Set `gatewayBaseUrl` to the Gateway origin, for example `http://localhost:8080`.
3. Obtain separate access tokens by logging in through Identity as Customer A, Customer B, and an Admin. Put the access tokens in the matching environment variables. Keep the values local; do not export or commit a populated environment.
4. Ensure Identity, Stylist and Gateway are running with the configured issuer, audience and signing keys. P03-A does not provision test accounts or change Identity/Gateway configuration.

## Run order

Run the collection in order:

1. **Create session as Customer A** sends `POST /stylist/api/v1/stylist/sessions` and saves the returned `session_id` to `sessionId`.
2. **Read own empty history as Customer A** expects HTTP 200, `items: []`, and `next_cursor: null`.
3. **Customer B cannot read Customer A history** expects HTTP 404, matching the same response used for a missing session.
4. **Admin cannot read another customer history** expects HTTP 404; Admin is self-only for these routes.

The collection checks status and selected response/cache headers. For a full pagination exercise, create messages only through a future approved request-processing flow; P03-A intentionally has no public message-write API, so a newly created session remains empty.

## Endpoints

- `POST /stylist/api/v1/stylist/sessions` — optional JSON `title`; a successful response is 201 and includes a `Location` header.
- `GET /stylist/api/v1/stylist/sessions/{sessionId}/messages?limit=20` — newest-first owner-scoped history; `limit` is 1–50, default 20. `before` is an opaque cursor returned by a later page when messages exist.

Both endpoints require a valid Identity-issued JWT and return `Cache-Control: no-store`. Customer and Admin tokens can access only sessions whose owner matches JWT `sub`.

## Evidence boundary

The collection and environment JSON were structurally parsed locally. No Postman/Newman runtime HTTP request was sent as part of this handover. Automated MockMvc tests and a disposable PostgreSQL Testcontainers integration suite are reported separately in `../../docs/planning/stylist/EP-07_P03A_SESSION_HISTORY_REPORT.md`.
