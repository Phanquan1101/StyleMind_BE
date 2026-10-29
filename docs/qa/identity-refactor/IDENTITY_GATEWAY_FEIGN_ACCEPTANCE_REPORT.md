# Identity refactor, Gateway and Feign acceptance report

**Date:** 2026-10-12
**Branch / base revision:** `dev` / `a924e8d7f064f643cff5a0c83629f96ebd6bc57e`
**Scope:** verification of the current Identity refactor, API Gateway routing, Notification Feign delivery, and the existing Register/Auth, Profile, Address, and Manage Users flows.

## Result

The requested local acceptance checks passed after one Postman scenario was rerun with its required precondition. No API contract, business behavior, migration, or business schema was changed. V1–V3 remain untouched. No persistent database or `stylemind-task4` resource was used.

All four source Postman collections were structurally validated: 221 requests and 407 embedded scripts compiled. Runtime calls used `http://localhost:8080` through the disposable Gateway stack. The source collections and their assertions were not edited.

## Verification evidence

| Area | Result |
|---|---|
| Notification service | `mvn -B -ntp -f notification-service/pom.xml verify` — 23 tests, 0 failures/errors/skips. |
| API Gateway | `mvn -B -ntp -f api-gateway/pom.xml verify` — 7 tests, 0 failures/errors/skips. Covers method/path/query/header/body forwarding, Problem Details/status/content/cache/location/retry headers, 204 and HEAD bodies, JWKS pass-through, internal Notification path variants, and bounded failure when Identity is stopped. |
| Identity | `mvn -B -ntp -f identity-service/pom.xml verify` — 144 tests, 0 failures/errors/skips in **two consecutive runs**. Testcontainers used disposable PostgreSQL. |
| Register integration | `RegisterPostgresIntegrationTests` — 13 tests passed after making Mailpit lookup select the newest message addressed to the current test recipient. |
| Production Feign proxy | 5 tests passed against a Spring-created production Feign bean and embedded HTTP servers. Both verification and reset routes/payloads/recipient/service token were checked. Exact 202 succeeds; 200/204 map to `EMAIL_DELIVERY_UNKNOWN`; 503 maps to `EMAIL_DELIVERY_FAILED`; timeout and closed connection map to `EMAIL_DELIVERY_UNKNOWN`; each request is attempted once. 301/302/307/308 to same-host and different-host targets are not followed. |
| Auth Postman flow | Fresh-account core run: 23 HTTP calls (including Mailpit prerequest calls), 19 assertions, all passed. Covers Register/email verification, Login, refresh/replay, forgot/reset password, password change, logout, negative cases, and public JWKS. OTP values and tokens were not included in the report/log output. |
| Resend Postman scenario | Run separately using a newly registered pending account after cooldown: `204`, 1 assertion passed. |
| Manage Profile Postman | 116 requests, 116 assertions passed. |
| Manage Addresses Postman | 39 requests, 75 assertions passed. Includes A/B ownership isolation, validation/no-mutation checks, address cleanup, and authentication failures. |
| Manage Users Postman | 46 requests, 138 assertions passed. Includes admin authorization, role/status changes, stale-token denial, and last-admin guards. |
| Live Gateway/OpenAPI | Gateway at `localhost:8080` served the identity OpenAPI document: 25 operations, 29 schemas, 1 security scheme; all 20 expected paths/methods were present. JWKS was also fetched through the Gateway. |
| Formatting / whitespace | `git diff --check` and `git diff --cached --check` passed. No staged changes were present. |

### Postman resend precondition

The first run of the full Auth collection executed its optional resend folder after the main flow had already verified that account. The service correctly returned `409` for resend on an already-active account, so that one optional assertion failed in that run. The Auth core flow was rerun successfully on a fresh account with the optional folder isolated; that folder was then run separately against a pending account after cooldown and returned the expected `204`. This is a collection ordering/precondition issue, not a service failure. The source collection was left unchanged.

## Gateway and Notification findings

- Gateway forwarded the request method, path, query string, Authorization header, correlation header, content type and body to Identity.
- Problem Details and response headers, including `Cache-Control`, `Location` and `Retry-After`, remained intact. 204 and HEAD responses had no response body.
- The Gateway did not route Notification internal paths or tested encoded/normalized variants to Identity.
- Stopping the disposable Identity upstream resulted in a finite 5xx response in under five seconds; the test observed no replay.
- The production Feign client did not follow same-host or cross-host redirects. Its tests checked both email delivery contracts and confirmed no second attempt. Only status 202 is treated as successful delivery.
- Existing Notification security tests ran in the Notification verification suite. No live Notification internal route was exposed through the Gateway.

## Files changed in this verification

- `identity-service/src/main/resources/application.properties` — use Spring Cloud OpenFeign millisecond connect/read timeout properties and explicitly disable redirects.
- `identity-service/src/test/java/com/stylemind/identity/NotificationFeignProductionTests.java` — added production-proxy HTTP contract, timeout, retry-count and redirect regression tests.
- `identity-service/src/test/java/com/stylemind/identity/RegisterPostgresIntegrationTests.java` — make test OTP retrieval recipient-specific to avoid selecting another test's email from Mailpit.
- `api-gateway/src/test/java/com/stylemind/gateway/GatewayRoutingTests.java` — added Gateway forwarding, response, JWKS, internal-path and stopped-upstream coverage.
- `docs/qa/identity-refactor/IDENTITY_GATEWAY_FEIGN_ACCEPTANCE_REPORT.md` — this report.

The repository already had many T0–T4 and other uncommitted/untracked changes before this verification. They were preserved. Nothing was staged, committed, pushed, reset, cleaned, or reverted.

## Database and Docker scope

The runtime checks used a disposable Compose project named `stylemind-identity-acceptance-20261012`, its own PostgreSQL 18 volume, Mailpit, Notification, Identity, and Gateway. Flyway V1–V3 ran only against that newly created disposable `stylemind_identity` database. Testcontainers databases were also disposable. The Compose project was taken down with its own volume, network, containers, and locally built images removed; verification found zero remaining containers and volumes with that project label. The existing persistent local databases and volumes were not touched.

Docker Desktop stop was requested after cleanup. A later CLI check could not retrieve engine status and prompted to start Docker Desktop, confirming the engine was stopped.

The generated test-only runtime environment, signing key, and synthetic Postman environment files remain under ignored `target/identity-refactor/acceptance/e2e/`. A workspace policy rejected the narrow file-deletion command; those files were not opened or printed. Sanitized per-attempt logs and the temporary Postman collection copies remain alongside them.

## Sanitized run logs

Run output is under the ignored `target/identity-refactor/acceptance/` directory. Logs were kept per attempt and redacted for OTP-like values, bearer credentials, and JWTs. Key logs include:

- `notification-verify-20261012T215524.log`
- `gateway-verify-20261012T220146.log`
- `identity-feign-production-20261012T220242.log`
- `register-integration-20261012T220940.log`
- `identity-verify-final-01-20261012T221111.log`
- `identity-verify-final-02-20261012T221256.log`
- `e2e/postman-auth-core-20261012T222812.log`
- `e2e/postman-auth-resend-20261012T222743.log`
- `e2e/postman-profile-20261012T222514.log`
- `e2e/postman-addresses-20261012T222527.log`
- `e2e/postman-manage-users-20261012T222543.log`

## Limitations

These results verify this local branch and disposable local topology only. They do not establish production topology, deployment readiness, or production SMTP/JWT secret configuration.
