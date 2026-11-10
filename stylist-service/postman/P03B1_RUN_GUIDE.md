# P03-B1 Postman run guide

This collection tests request intake and owner-scoped read-back. A successful POST stores a request as `RECEIVED`; it does not start generation or call another service.

## Import and configure

1. Import `StyleMind-Stylist-P03B1.postman_collection.json` and `StyleMind-Stylist-P03B1.postman_environment.json`, then select the environment.
2. Set `gatewayBaseUrl` to the local Gateway origin, normally `http://localhost:8080`.
3. Enter separate Identity-issued access tokens for Customer A, Customer B, and Admin. Do not export or commit an environment after entering tokens.
4. Runtime execution requires Gateway and Stylist to be running against a **disposable Stylist test database** with V1 and V2 applied. Do not apply V2 to the persistent local/team database for this collection. The automated Testcontainers suite is the approved migration/runtime verification for this slice.

## Run order

Run requests 1–9 in order:

1. Create a session as Customer A; the test script saves `sessionId`.
2. Submit A's manual styling request with a USER message and one color exclusion; the script saves `requestId` and `sourceMessageId`. Expect `201`, `RECEIVED`, `generation_started: false`, `Location`, and `Cache-Control: no-store`.
3. Submit a second structured request referring to `sourceMessageId`; expect the same USER message ID and no copied message text.
4. Read the first request using Customer A; expect `200` and the stored context/constraint data.
5. Read the same request as Customer B; expect concealed `404 RESOURCE_NOT_FOUND`.
6. Read it as Admin; expect `404 RESOURCE_NOT_FOUND`. Admin is self-only and is not a customer-data bypass.
7. Send a GET with `{}` body to the percent-encoded `/stylist/%72equests/{requestId}` path. The request is expected to reach the same MVC resource route and be rejected by the request-policy filter with `400 INVALID_REQUEST` and `Cache-Control: no-store`. A `404` is not a successful security result for this check.
8. Submit a request whose message contains only NBSP/narrow-NBSP. Expect `400 INVALID_REQUEST`; no message/request/constraint is stored.
9. Submit valid context containing JSON-escaped U+0000 in `general_weather_context`. Expect `400 INVALID_REQUEST`, not `500`; no message/request/constraint is stored.

The submitted `message` is appended as a USER message and appears in the existing session history. The response does not include message content; use the existing history API for it. No API in P03-B1 creates a recommendation or assistant response.

## Endpoints

- `POST /stylist/api/v1/stylist/sessions/{sessionId}/requests` — validates and atomically stores request, optional USER message, and constraints.
- `GET /stylist/api/v1/stylist/requests/{requestId}` — reads the request only when its session belongs to the JWT subject.

Automated PostgreSQL integration tests use disposable Testcontainers databases and verify V1/V2. Postman/Newman runtime through Gateway is a separate evidence layer and has not been run as part of source implementation unless recorded in the implementation report.

The collection now includes the encoded-path, Unicode-blank, and NUL-negative cases. It does not duplicate every automated JWT/transaction/concurrency test. Use Identity-issued tokens only; do not paste tokens into exported or committed environment files. The encoded path check is meaningful only when it returns the expected `400 INVALID_REQUEST` from the mapped resource request; an unmapped-path `404` does not prove the route policy ran.
