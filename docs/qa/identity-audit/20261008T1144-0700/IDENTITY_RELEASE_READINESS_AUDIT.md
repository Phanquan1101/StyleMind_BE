# StyleMind Identity Release Readiness Audit

**Scope:** EP-02 / US-02.01–US-02.04, Identity → Notification mail delivery, and build/test regression for the four other services. Manage Profile was excluded.

**Overall:** Core flows passed the executed local automated and disposable-stack checks. This is not a production-readiness sign-off. The committed working configuration cannot start Identity because the ignored local HMAC environment value is not valid Base64; the audit used a disposable Compose override. External mailbox delivery was not tested. One initial Identity suite run had transient failures that did not recur; their detailed failure output was overwritten by subsequent Maven runs, so the initial Register failure cause remains unconfirmed.

## 1. Audit baseline and environment

| Item | Observed |
|---|---|
| Workspace | `C:\Users\FPTSHOP\Desktop\StyleMind_BE` |
| Branch / HEAD | `main` / `b46bd5bd1d77af83edd3de5969c9cc707f7c183c` |
| Audit time | 2026-10-08, approximately 10:55–11:44 Asia/Bangkok (UTC+07:00) |
| Baseline worktree | 20 tracked files modified and 171 untracked files already present before audit; the groups are listed below. No baseline changes were reverted. |
| Tooling | Java 21.0.8; Maven 3.9.11; Spring Boot 3.5.16; Flyway 11.15.0; Docker Engine 28.5.2; Docker Compose 2.40.3-desktop.1; PostgreSQL 18.6; Mailpit `axllent/mailpit:v1.31.4`. No Maven wrapper was present. |
| Compose validation | `docker compose --project-name stylemind-task4 --env-file .env config --quiet` passed. This validates interpolation/config structure, not application startup. |
| Gateway | No API Gateway is configured. E2E requests targeted the services directly. |

The 20 pre-existing tracked modifications were `.gitignore`, root `README.md`, six service POMs, six service `application.properties` files and six service smoke-test files. The 171 pre-existing untracked files were grouped under `.env.example` (1), `commerce-service` (11), `database` (2), `docker-compose.yml` (1), `identity-service` (98), `notification-service` (23), `payment-service` (11), `postman` (1), `scripts` (1), `stylist-service` (11), and `wardrobe-community-service` (11). These include the existing local implementation under audit.

Audit-created repository changes are limited to:

- Test-harness repair in [NotificationEmailClientTests.java](../../../../identity-service/src/test/java/com/stylemind/identity/NotificationEmailClientTests.java): the local HTTP test server now consumes request bodies, including chunked bodies, before responding. This fixes the fixture’s premature connection close; it does not change application code or expected results.
- This audit report.

No production Java, public API, security policy, migrations, Compose files, `.env`, workbook, database data, or other service source was changed. No commit or push was made. The workbook `C:\Users\FPTSHOP\Desktop\StyleMind_Task.xlsx` was read only; EP-02 rows 6–18 still show `To Do`, despite local implementation/tests being present.

## 2. Migration and database integrity

### Source migrations

| Version | Tables created | Source SHA-256 |
|---|---|---|
| V1 | `account`, `account_role`, `user_profile`, `customer_address`, `style_profile`, `profile_style`, `profile_color`, `profile_brand`, `refresh_session` | `937A0F0B96858F73E55B5C7D5B74A001AB4BD66723139FB5E043F12324E5CF25` |
| V2 | `email_verification_challenge` | `FC823A12C9B89F94ABD7E91A87906B841712DE310FC141EAD3DFB5846019703F` |
| V3 | `password_reset_challenge` | `57F226251E959E983213E014F6E279E176FCCCAADC4D350A06CA69E5A674BF48` |

V1 supplies primary keys, the unique account email, unique refresh-token hash, local account/profile/session foreign keys, the `(account_id, role_code)` key, and the `CUSTOMER`/`ADMIN` role check. `profile_brand.brand_id` is a logical Commerce reference with no cross-service FK. V2 and V3 each have one unique challenge per account, 32-byte digest checks, bounded attempt/send counts, lock/grant consistency checks, and expiry/window checks. V3 has a unique nullable reset-token digest. No cross-service foreign key or explicit non-constraint secondary index appears in these migrations. The reviewed migration definitions match the implemented usage; no migration was run by the audit against a persistent database.

### Persistent local target (metadata only)

The target was resolved by Docker labels to container `stylemind-task4-postgres-1` (PostgreSQL image `postgres:18`), Compose project `stylemind-task4`, service `postgres`, database `stylemind_identity`, role `stylemind_identity_app`, volume `stylemind-task4_postgres-data`. It was stopped at audit start, started only for read-only metadata, and stopped again. PostgreSQL reported 18.6. Flyway history was:

| Rank | Version | Description | Flyway checksum | Success |
|---:|---:|---|---:|---|
| 1 | 1 | create identity tables | 906976081 | true |
| 2 | 2 | create email verification challenge | -510737541 | true |
| 3 | 3 | create password reset challenge | -474948533 | true |

No account, challenge, email, password hash, token digest or session rows were selected. No persistent row or volume was modified. Source SHA-256 values above were rechecked after the audit and were unchanged. The disposable E2E database was separate, then its exact audit-labeled containers, network and volume were removed.

## 3. Endpoint and rule inventory

The API contract below is observed in `identity-service/README.md`, controllers, DTOs and security configuration. Identity’s exact auth POST routes are public; other `/api/**` routes require JWT. Notification’s two internal POST routes require `X-StyleMind-Service-Token`. Health and OpenAPI are public. The Notification service is not published on the default Compose host ports; the audit used a temporary port mapping for the direct HTTP check.

| Method / path | Access | Request → response | Verified side effects / notable errors |
|---|---|---|---|
| `POST /api/v1/auth/register` | Public | `email,password` → `201 account_id,email,account_status` | Stores normalized email, one-way password hash, pending account, CUSTOMER role and verification challenge transactionally, then delivers mail. Duplicate normalized email → `EMAIL_ALREADY_EXISTS`. SMTP definite/unknown failures retain the committed pending account and give a resend recovery action. |
| `POST /api/v1/auth/verify-email` | Public | `email,code` → `204` | Consumes current challenge and activates eligible account; malformed/invalid/expired/locked/used cases return sanitized problem responses. Duplicate verify → conflict. No token issued. |
| `POST /api/v1/auth/resend-email-verification` | Public | `email` → `204` after accepted delivery | Rotates verification challenge; cooldown and fixed-window send quota; delivery failure does not silently mark verification successful. |
| `POST /api/v1/auth/login` | Public | `email,password` → `200 access_token,refresh_token,token_type,expires_in` | Unknown email/wrong password share `401 INVALID_CREDENTIALS`; after correct password, non-ACTIVE/unverified/no-allowed-role accounts get `403 ACCOUNT_NOT_ACTIVE`. Success uses current database roles and creates a refresh session. `Cache-Control: no-store`. |
| `GET /.well-known/jwks.json` | Public | No body → public RSA JWK set | Public fields only; `kid` corresponds to signing key. |
| `POST /api/v1/auth/refresh` | Public | `refresh_token` → `200` new access and rotated refresh token | Old token consumed once; new session preserves absolute expiry. Invalid, expired, revoked and replayed tokens share unauthorized response. No-store. |
| `POST /api/v1/auth/logout` | Public | `refresh_token` → `204` | Idempotently revokes only the identified refresh session; does not revoke already-issued access JWT. No-store. |
| `POST /api/v1/auth/forgot-password` | Public | `email` → generic `202` for a valid request | Only verified ACTIVE account receives reset OTP; unknown/ineligible account creates no email/account. Delivery/rate outcomes remain generic. |
| `POST /api/v1/auth/verify-password-reset` | Public | `email,code` → `200 reset_token,expires_in` | Reset OTP consumed once; returns one-use opaque five-minute token. No-store. |
| `POST /api/v1/auth/reset-password` | Public | `reset_token,new_password` → `204` | Password update, grant consumption and revocation of all unrevoked refresh sessions are one transaction. Does not activate account or revoke access JWTs. |
| `POST /internal/v1/email-verifications` | Internal service token | `recipientEmail,verificationCode` → `202` | Fixed verification template; sends only after SMTP accepts. Other/unauthenticated requests do not send. |
| `POST /internal/v1/password-resets` | Internal service token | `recipientEmail,resetCode` → `202` | Separate fixed reset template; same send/auth properties. |

Register/Login email handling trims and lowercases consistently; Login preserves the supplied password. Account creation uses `PENDING_VERIFICATION` and `CUSTOMER`; verification changes to `ACTIVE` and sets `email_verified_at`. These are implementation facts and local test observations. The code does not provide account-state administration or a production lock/unlock feature; a non-ACTIVE fixture used by tests proves denial, not a lock-management API.

## 4. Business traceability and functional review

| Audit ID | Requirement / confirmed rule | Implementation and evidence | Result / limit |
|---|---|---|---|
| AUD-REG-001 | Register stores normalized email and password hash, assigns CUSTOMER, begins pending verification; client cannot choose role/state. | `RegisterController` → `RegisterService` → transactional persistence/repositories; `RegisterPostgresIntegrationTests`, `RegisterServiceTests`; disposable live flow asserted one pending account, CUSTOMER, challenge and no plaintext password. SMTP happens after commit. | **PASS** for exercised paths. Invalid SMTP result leaves pending account and recovery action. Exhaustive malformed/oversize-body matrix across every field was not run. |
| AUD-REG-002 | Unique normalized email; duplicate registration must not add a second account/challenge or send another mail. | Unique `account.email`; duplicate and normalization integration test; simultaneous registration/unique constraint behavior included in PostgreSQL suite. | **PASS** for normalized duplicate and exercised concurrency. Full arbitrary email-normalization edge corpus not tested. |
| AUD-VERIFY-001 | Six-digit string OTP preserves leading zero; OTP is account/challenge-bound, digested, short-lived, one-use, and cannot activate twice. | `VerificationCodeServiceTests`; PostgreSQL Register integration tests for wrong/cross-account/expired/malformed/consumed and concurrent verify/resend. | **PASS** for listed test cases. Clock-controlled exact expiry boundary is covered; not every before/after boundary was separately stress repeated. |
| AUD-RESEND-001 | 60-second cooldown, max three sends per fixed hour, five bad attempts lock only challenge for 15 minutes; resend must not clear bad-attempt count. | V2 checks; Register integration tests for cooldown/window rollover, fourth-send limitation, failed-send accounting, lock, resend rotation and concurrent verify/resend. | **PASS** for tested scenarios. Per-account protection exists; no IP/global quota was found. |
| AUD-LOGIN-001 | Known credential required; unknown email and wrong password are indistinguishable; only verified ACTIVE account with database role receives tokens. OTP lock is not account login lock. | `LoginService`, `LoginSessionService`; PostgreSQL Login suite covers valid, wrong/unknown, pending/locked/unverified, missing role, corrupt hash, OTP lock independence and status/hash recheck races. | **PASS**. “Locked” is a non-ACTIVE test fixture only; no account lock lifecycle exists. |
| AUD-JWT-001 | Access JWT is distinct from opaque refresh token; RS256 signing key remains stable; JWKS is public-only; role claims map to authorities. | JWT config/application suites plus live Compose E2E: validated actual signature against JWKS, `kid`, required claims, issuer/audience, role mapping, 900-second default TTL, no private JWKS values. | **PASS** for current local contract/key. Production key provisioning/rotation and cross-service adoption were not assessed. |
| AUD-REFRESH-001 | Refresh is one-use, rotates atomically, stores only digest, preserves absolute expiry and does not revoke unrelated sessions. | PostgreSQL suite covers unknown/expired/revoked/replay, two concurrent refreshes, refresh/logout race, signing/insert rollback, role/state recheck; live test confirmed replay rejection and digest-only persistence. | **PASS** for tested cases. No extended multi-instance stress run. |
| AUD-LOGOUT-001 | Logout revokes only supplied session and is idempotent; access JWT stays valid until expiry. | PostgreSQL suite covers old rotated token, unknown/expired/inactive, parallel logout, failure rollback and other sessions; live test confirmed post-logout refresh rejection. | **PASS** for tested cases. |
| AUD-RESET-001 | Forgot-password response is generic; only eligible verified ACTIVE account receives mail; registration OTP is not interchangeable. | `PasswordResetService` / persistence; reset PostgreSQL + Mailpit tests and live E2E checked eligible recipient only, no mail/account for unknown/pending address, generic 202, and cross-purpose rejection. | **PASS** for exercised valid-request paths. Synchronous delivery may vary in response time; docs acknowledge timing-based enumeration is not eliminated. |
| AUD-RESET-002 | Reset OTP is one-use; reset token is opaque, digest-only, short-lived and one-use. A new OTP must not destroy a live reset grant. | V3 constraints and PostgreSQL tests cover leading-zero/invalid/expired/fifth attempt, exact expiry, replay, active grant preservation and challenge separation. | **PASS** for tested cases. |
| AUD-RESET-003 | Successful reset atomically updates password, consumes grant and revokes every active refresh session; does not change account state/role or invalidate access JWT. | Transaction service/PostgreSQL tests cover atomic rollback, concurrent reset/refresh and state preservation; E2E checked old password/refresh fail, new password works, unrelated account unaffected. | **PASS** for tested cases. |
| AUD-NOTIFY-001 | Internal email endpoint accepts only exact service-authenticated POST requests and confirms delivery only on contract HTTP 202. | Shared `RequestMatcher` security/filter; embedded-server tests cover canonical/encoded mapped paths, missing/wrong/correct token and other methods. Client test server now drains Content-Length/chunked bodies; client tests assert 202 success, 200/204 unknown, 503 failed, timeout unknown. | **PASS** for tested contract. No auth bypass was reproduced; live encoded route without token returned 401 and did not send. |

HTTP input handling includes Bean Validation for email, nonblank fields, six-digit OTP format, token format and new-password policy. Tests exercise sanitized malformed JSON, representative invalid input, unexpected login fields, UTF-8 password byte boundary, and token/OTP failures. **NOT TESTED as a complete matrix:** absent body, wrong content type/Accept, null/empty/whitespace/overlong values for every request DTO, mass-assignment probes on every route, and very large JSON request bodies. These gaps are test-coverage limitations; no failing behavior was observed in the covered cases. No explicit application-level JSON body-size cap or IP/global rate limiter was found.

## 5. Automated test execution

All commands ran from the repository root using installed Maven. Maven Surefire XML under each module’s `target/surefire-reports` was reconciled with console output. These projects use `*Tests` suites in Surefire; no separate Failsafe integration-test phase was found. PostgreSQL/Notification/Mailpit suites started real disposable Testcontainers during this run rather than silently skipping. The four non-Identity/Notification modules received baseline build/test checks only.

| Command | Suites | Tests | Failures | Errors | Skipped | Notes |
|---|---:|---:|---:|---:|---:|---|
| `mvn -f identity-service/pom.xml -Dtest=NotificationEmailClientTests test` | 1 | 5 | 0 | 0 | 0 | Targeted regression after test-fixture repair. |
| `mvn -f identity-service/pom.xml verify` (final run) | 10 | 82 | 0 | 0 | 0 | Includes disposable PostgreSQL, Registration + Notification/Mailpit, JWT/Login, Refresh/Logout, Reset, HTTP and unit suites. |
| `mvn -f notification-service/pom.xml verify` | 4 | 17 | 0 | 0 | 0 | Includes actual embedded HTTP-server auth tests. |
| `mvn -f commerce-service/pom.xml verify` | 1 | 13 | 0 | 0 | 0 | Bootstrap/security baseline only. |
| `mvn -f payment-service/pom.xml verify` | 1 | 13 | 0 | 0 | 0 | Bootstrap/security baseline only. |
| `mvn -f wardrobe-community-service/pom.xml verify` | 1 | 13 | 0 | 0 | 0 | Bootstrap/security baseline only. |
| `mvn -f stylist-service/pom.xml verify` | 1 | 13 | 0 | 0 | 0 | Bootstrap/security baseline only. |
| **Final verify total (six services)** | **18** | **151** | **0** | **0** | **0** | Targeted 5-test run is additional and not included in 151. |

**Initial run signal:** the first full Identity verify reported 82 tests with 5 failures and 1 error; the Register suite then passed in an isolated 13/13 rerun. A later full rerun exposed the Notification client test fixture closing before reading the JSON request body. After fixing the fixture—not production behavior or expected statuses—the targeted 5/5 and final full Identity 82/82 passed. Earlier Surefire output was replaced by later runs, so the precise root cause for the first five Register failures cannot now be re-established. Treat this as a test-stability risk until repeated CI runs are clean. No test was intentionally skipped.

## 6. Disposable Docker E2E

The audit stack used a unique Compose project and a separate disposable PostgreSQL volume. It overrode mail transport to local Mailpit (`mailpit:1025`), disabled SMTP auth for that local transport, and used one-off test HMAC/service-token values in a temporary override. It did not use the persistent database or contact an external mail provider. The override values are not included here.

| Scenario | Observed result |
|---|---|
| E2E-01 Register → verify → Login → JWT/JWKS → Refresh → Logout | Register returned 201 and committed pending CUSTOMER/challenge; Mailpit got the verification message; verify returned 204; repeat verify conflicted; bad password denied and good Login returned 200. Real access JWT verified against public JWKS; refresh succeeded once, replay failed, logout returned 204 and subsequent refresh failed. Disposable DB held only the refresh digest, not the plaintext refresh token. |
| E2E-02 Forgot → verify reset OTP → reset password | Two disposable accounts used. Only eligible A received reset email; A’s OTP with B’s email failed; verification/reset grants were one-use; old password failed and new password worked; pre-reset refresh session was revoked; B remained usable and unchanged. Reset responses used `no-store`. |
| E2E-03 Unknown email | Valid forgot request returned the generic 202; no account or new mail was created. |
| E2E-04 Pending account | Login returned the expected not-active error; Forgot returned generic response without reset email and did not activate the account. |
| E2E-05 Notification internal auth/path | Missing/wrong token did not call sender; valid token sent exactly one expected message. Encoded mapped path without token returned 401. Other HTTP methods did not send. No bypass was reproduced. |
| E2E-06 resend cooldown | Immediate resend was limited (429) with no extra Mailpit message. Fixed-window/attempt boundary behavior was additionally covered by disposable PostgreSQL tests. |
| E2E-07 Notification outage/recovery | With only the audit Notification container stopped, Register returned `504 EMAIL_DELIVERY_UNKNOWN` while the account remained committed and pending; no mail was sent. Notification restart followed by the allowed resend/verify path recovered the account. No automatic duplicate send occurred. |

The live stack used images built from the working tree. Identity and Notification health checks were UP before E2E, and direct API behavior was exercised. SMTP provider delivery outside Mailpit is **NOT TESTED**. Identity/PostgreSQL restart-and-reconnect lifecycle, cross-instance deployments, and prolonged load were **NOT TESTED**. The disposable project’s containers/network/volume were removed after confirming their project labels. Other Docker projects were left alone.

## 7. Findings

| ID | Area / severity | Type and evidence | Impact | Recommended action / retest |
|---|---|---|---|---|
| F-01 | Local Compose / **P2** | **Observed configuration defect.** The ignored local `STYLEMIND_VERIFICATION_HMAC_SECRET` does not pass strict Base64 parsing. Identity startup exits with a configuration exception when Compose uses current `.env`. `docker compose config --quiet` still passes because it checks Compose interpolation, not the application’s Base64 validity. | Identity cannot start with the current local `.env`, so a normal local Postman/demo stack is not currently reproducible without the audit override. Replacing HMAC key carelessly can invalidate already-issued challenges. | Owner should restore a valid existing key or decide challenge impact before rotation; do not copy any audit one-off value. Retest Identity startup and a register/verify + forgot/reset flow using the normal `.env`. |
| F-02 | Public unauthenticated auth/email endpoints / **P2** | **RISK, not confirmed defect.** Per-account OTP cooldown/send/attempt limits exist; no IP/global registration or email-send quota was found in repository configuration. External edge controls were not assessed. | Distributed callers can vary email addresses and create mail/CPU/provider load if deployed without edge controls. | Decide public-deployment controls (for example, gateway/WAF/provider quotas) before public exposure. Do not treat this as accepted or add a new mechanism as part of this audit. Retest only after an approved decision. |
| F-03 | Backlog traceability / **P3** | **Observed tracking mismatch.** Workbook `Backlog` rows 6–18 for US-02.01–04 remain `To Do`, while local source/tests and this audit demonstrate implemented flows. Workbook was intentionally not changed. | Project tracking may misstate delivery status. | Project owner should reconcile backlog statuses after reviewing this report; not changed in this audit. |
| F-04 | Audit temp cleanup / **P2 operational hygiene** | **Observed cleanup limitation.** The disposable stack and volume were removed, but `C:\Users\FPTSHOP\AppData\Local\Temp\stylemind-identity-audit-20261008` remains with the Mailpit Compose override, direct-Notification port override and four service verification logs. The Mailpit override contains one-off audit-only HMAC/service-token values. Attempts to remove this exact temp directory were rejected by command safety review. Values are not reproduced in the report. | The one-off local values remain on disk, although their disposable stack has been removed and the values were not reused. | Delete that exact directory after taking any needed non-sensitive logs, then verify it is absent. Do not use those one-off values as local application secrets. |
| F-05 | Identity test stability / **P2 risk** | **Not reproduced after reruns; root cause partly TBD.** First full run had 5 failures + 1 error; isolated Register and final full suite passed. A separate confirmed HTTP client fixture defect was repaired. Earlier detailed Surefire failure output was overwritten. | A one-off first-run failure cannot be conclusively attributed, so current green run does not establish repeatability. | Run the final Identity suite a few times in CI with isolated PostgreSQL/Mailpit resources; keep diagnostics redacted and verify no failures recur. Do not change product expected results to accommodate flakes. |

No P0/P1 finding was confirmed in the scenarios exercised. This is bounded to local tests and the listed scenarios; it is not a claim that no other defect exists.

## 8. Per-function status and readiness

| Function | Status | Main evidence / remaining limit |
|---|---|---|
| Register | **PASS — tested scope** | PostgreSQL integration, transaction failures, normalized duplicate, concurrency, Mailpit delivery/recovery, live API. First-run flake remains tracked as F-05. |
| Verify email | **PASS — tested scope** | Expiry, attempts/lock, account binding, replay, concurrency and live activation. |
| Resend verification | **PASS — tested scope** | Cooldown/window, rotation, lock interaction, delivery failure/recovery and live rate limit. |
| Login | **PASS — tested scope** | Credential/state/role, real signed tokens, persistence and race/rollback checks. Test fixture `LOCKED` is not a state-management feature. |
| JWT/JWKS | **PASS — local contract** | RS256 signature, claims, TTL, JWKS key match/public-only and authorities verified. Production distribution/rotation not assessed. |
| Refresh | **PASS — tested scope** | Rotation, replay, digest-only storage, absolute expiry, concurrency and rollback. |
| Logout | **PASS — tested scope** | Session-scoped/idempotent revoke, races, unrelated session preservation; access JWT remains valid. |
| Forgot Password | **PASS — tested scope** | Generic response, eligible-only email, no account enumeration by body, request limits, Mailpit delivery and recovery. Timing side-channel is documented. |
| Verify reset OTP | **PASS — tested scope** | Purpose separation, one-use grant, attempt/expiry/boundary and concurrent verification. |
| Reset Password | **PASS — tested scope** | One-use grant, password change + grant consumption + session revocation atomically, race/rollback, unaffected other account. |
| Notification internal/SMTP via Mailpit | **PASS — local Mailpit only** | Embedded real HTTP server auth/path tests, live auth and exact-202 handling, recipient/template, SMTP failure classification. External mailbox is not tested. |
| Commerce / Payment / Wardrobe Community / Stylist | **PASS — baseline build/test only** | 13 smoke/security/bootstrap tests per service; no service-specific business/security audit. |

| Readiness dimension | Result |
|---|---|
| Automated regression | **PASS with F-05 repeatability caveat:** 151 final verify tests, 0 failures/errors/skips. |
| Local Compose using current `.env` | **NOT READY:** Compose syntax validates, but Identity refuses its HMAC setting. Disposable override proved the application paths, not default local configuration. |
| Real mailbox delivery | **NOT TESTED:** audit email was confined to Mailpit. |
| Public/production deployment | **NOT READY / outside scope:** production topology and service authentication remain local choices; external SMTP/TLS, key provisioning/rotation, edge rate controls, deployment secrets, and operational recovery were not validated. |
| Proceed to Manage Profile | **Not yet on this workstation’s normal local stack.** Resolve F-01 without unsafe HMAC rotation, remove the exact test-temp directory (F-04), and rerun a short normal-`.env` local smoke. The core Identity Register/Login/Refresh/Logout/Forgot/Reset flows passed the audit scenarios. No Manage Profile work was started. |

### Suggested remediation order

1. **Before local integration/demo:** resolve F-01 with the key owner, remove the exact F-04 temp directory, and rerun default Compose plus one registration/verification and one recovery smoke.
2. **Before relying on the green suite as a stable gate:** repeat Identity verify in CI to close F-05; investigate any recurrence from fresh redacted reports.
3. **Before public deployment:** decide edge-level IP/global abuse controls (F-02), validate the selected real SMTP provider/TLS and production key/service-auth topology, and test restart/recovery against the eventual topology.
4. **Project tracking:** reconcile the EP-02 backlog rows when authorized (F-03). No backlog edit is included here.

## 9. Final scope and limitations

- No source-level production defect was changed or worked around. The only repository code edit is the test-only HTTP fixture repair described above.
- No Login/Refresh/Logout/Forgot behavior was extended; no schema, migration, API, business rule, or workbook was changed.
- No persistent database business rows were inspected or changed. Persistent PostgreSQL was left **stopped**, as it was before the audit; its volume was retained. The audit project’s disposable database/volume was removed. No test data remains in either Mailpit instance from the audit.
- Docker Desktop remains running; unrelated containers and volumes were untouched.
- `git diff --check` passed. The pre-existing tracked CRLF conversion warnings remain informational. The changed untracked test fixture was separately scanned for trailing whitespace and had none.
- Findings distinguish confirmed observations from risks and limits. The audit does not certify Manage Profile, all EP-02 backlog status, all input combinations, external SMTP, production deployment, or absence of all defects.
