# Identity Audit Follow-up

- **Project:** `C:\Users\FPTSHOP\Desktop\StyleMind_BE`
- **Scope:** EP-02 / US-02.01–US-02.04 Identity, its Notification email contract, repeatability, and local recovery. Manage Profile and all other services remain out of scope.
- **Result:** Final validation matrix, three repeated Identity verifies, Notification verify, and disposable restart/recovery checks passed. Default `.env` full email smoke remains blocked because its SMTP target is not Mailpit. This report supplements and does not replace [IDENTITY_RELEASE_READINESS_AUDIT.md](IDENTITY_RELEASE_READINESS_AUDIT.md).

## 1. Baseline and changes

- Branch: `main`; HEAD remained `b46bd5bd1d77af83edd3de5969c9cc707f7c183c` throughout. No commit or push was made.
- The working tree contained the existing T0–T4 changes. They were retained. No business implementation, production security policy, API contract, SQL migration, database schema, or workbook was changed in this follow-up.
- Test/config evidence was based on the current repository and the approved local `.env`. The local HMAC value was replaced only after the owner approved a new key and accepted that the old challenge might stop working. The value is not in this report or Git; `.env` remains ignored.
- The final source/test fingerprint covers 122 files beneath `identity-service/src` and `notification-service/src`. Before/after manifests match. Manifest files are in `follow-up-runs/20261008T1405Z/`.

### Files changed or added for this follow-up

- Test-only: `identity-service/src/test/java/com/stylemind/identity/NotificationEmailClientTests.java` (finite body-reading fixture and protocol probes).
- Test-only: `identity-service/src/test/java/com/stylemind/identity/IdentityRequestValidationMatrixTests.java` (HTTP validation, unknown-field, side-effect, and password-whitespace probes).
- Test-only: `notification-service/src/test/java/com/stylemind/notification/InternalEmailVerificationHttpSecurityTests.java` and `InternalPasswordResetHttpSecurityTests.java` (embedded-server validation and sender side-effect probes).
- Audit evidence: this report, `disposable-recovery-summary.md`, `disposable-recovery-attempts.md`, two audit runner scripts, a port-only disposable Compose override, sanitized logs, hash manifests, and Surefire XML copies in this directory tree.
- Local-only configuration: ignored `.env` HMAC key. No key, token, OTP, password, private key, or SMTP credential is recorded in audit artifacts.

No production source file was edited. The added runner/override files are audit tools and do not participate in application builds.

## 2. Endpoint validation matrix

The expected invalid-request result comes from the current endpoint validation/error behavior. No API contract was changed. For all nine Identity POST routes, the matrix ran missing body, malformed JSON, JSON `null`, `Content-Type: text/plain`, and GET instead of POST:

| Probe | Observed result | Side-effect evidence |
|---|---|---|
| Missing body, malformed JSON, JSON `null` | `400` on every Identity route | Mockito service beans had no interactions. |
| Wrong content type (`text/plain`) | `415` on every Identity route | No service interaction. |
| GET instead of the route's POST | `401` on every Identity route | No service interaction. The exact POST routes are public; other `/api/**` requests remain protected. The wrong-method behavior is the current security fallback, not a controller `405` contract. |

Per-route field probes were:

| Identity route | Invalid values exercised |
|---|---|
| `POST /api/v1/auth/register` | Missing/null/empty/blank/invalid/wrong-type email; missing/null/empty/blank/wrong-type/short/73-byte password. Exactly 72 UTF-8 bytes accepted in disposable runtime; 73 rejected before database or email side effects. |
| `POST /api/v1/auth/verify-email` | Missing/null/empty/blank/invalid/wrong-type email; null/empty/short/long/non-digit/whitespace/wrong-type code. A six-digit leading-zero string remains a string. |
| `POST /api/v1/auth/resend-email-verification` | Missing/null/empty/blank/invalid/wrong-type email. |
| `POST /api/v1/auth/login` | Missing/null/empty/blank/invalid/wrong-type email; missing/null/empty/blank/wrong-type/73-byte password. Existing integration tests verify exact 72-byte UTF-8 credentials and that Login preserves surrounding password whitespace without applying the creation policy. |
| `POST /api/v1/auth/refresh` | Missing/null/blank/wrong-type token; short, 44-character, invalid-alphabet, padded, and leading/trailing-whitespace tokens. |
| `POST /api/v1/auth/logout` | Same malformed token classes as Refresh. |
| `POST /api/v1/auth/forgot-password` | Missing/null/empty/blank/invalid/wrong-type email. A structured 255-character email returned generic `202`; for an unknown address it created no account and sent no email. |
| `POST /api/v1/auth/verify-password-reset` | Missing/null/empty/blank/invalid/wrong-type email; missing/null/empty/short/long/non-digit/whitespace/wrong-type code. |
| `POST /api/v1/auth/reset-password` | Missing/null/blank/wrong-type/short/44-character/invalid-alphabet/padded reset token; missing/null/empty/blank/wrong-type/short/73-byte new password. |

The DTOs have no explicit email maximum and the account column is PostgreSQL `text`. The disposable runtime accepted a structured 255-character Register email (`201`, one account and one Mailpit message). This is the observed current behavior; no email-length policy was inferred or added. Whether the product wants an email limit remains **TBD/PROPOSED**, not a confirmed defect.

Notification's two embedded HTTP-server tests exercised missing/malformed/null bodies; absent/null/empty/blank/invalid/wrong-type recipient and code; short/long/non-digit code; wrong content type; missing body; and GET/PUT/DELETE. Invalid bodies returned `400`, wrong content type returned `415`, and the sender was never invoked. For mapped endpoint paths, missing or incorrect service token returned `401`; the correct token returned contract `202` and invoked the sender once. GET/PUT/DELETE with the correct token returned `401` and never invoked the sender. Percent-encoded path variants accepted by Spring were exercised against the same embedded server and remained protected; no auth bypass was reproduced.

The Notification DTO has no explicit email maximum either. Both internal routes accepted the structured 255-character address with `202`; this does not establish a product maximum or a real-provider delivery result.

### Unknown/internal fields and state isolation

- Identity DTO probes supplied `account_id`, `role`/`roles`, `account_status`, `email_verified_at`, `password_hash`, `recipient_email`/`recipientEmail`, `expires_at`, `failed_attempts`, and `revoked_at`.
- Register/verification/resend/forgot/reset DTOs passed only declared contract properties to service methods. Login/Refresh/Logout reject unknown fields with `400` and no service interaction.
- In the disposable PostgreSQL flow, forged Register fields still produced a pending `CUSTOMER` with no password/hash/code in the response. Verifying A with a forged target B and future activation fields activated only A; B remained pending, and neither account's role changed. A's OTP did not verify B.
- The real Notification service ignored the extra `recipient_email` and sent to the declared `recipientEmail` only. Missing/incorrect internal tokens created no Mailpit message.
- The disposable flow reset A while B had a live session: A's pre-reset refresh token failed, B's refresh token still worked, and A's pre-reset access JWT stayed valid until expiry. PostgreSQL state confirmed the reset grant was consumed, A's password was not plaintext, and B stayed active.
- Invalid Identity field requests are stopped at DTO/service boundaries by the matrix tests. The 73-byte runtime Register probe additionally confirmed no account or Mailpit side effect.

## 3. Registration, OTP, token, and client coverage

The final Identity suite includes existing PostgreSQL-backed cases for normalized duplicate email, OTP leading zero, purpose/account binding, exact expiry boundary, malformed OTP not incrementing attempts, attempt/send limits, cooldowns, resend rotation, and transactional rollback. Clock-controlled boundary tests were not replaced with sleeps. Existing Login tests confirm password whitespace is preserved, exactly 72 UTF-8 bytes work, and 73 bytes are rejected. The expanded HTTP matrix separately verifies password values are not trimmed by Register/Login/Reset controllers.

`NotificationEmailClientTests` passes 7/7 in the final full Identity suite and in the targeted client run. The test server now:

- consumes exactly `Content-Length` bytes and drains chunked request bodies;
- applies a finite two-second socket timeout and bounded client/join timeouts;
- proves an incomplete chunked body terminates instead of hanging;
- preserves response assertions: `202` succeeds; `200`/`204` map to `504 EMAIL_DELIVERY_UNKNOWN`; `503` maps to `503 EMAIL_DELIVERY_FAILED`; a read timeout maps to `504 EMAIL_DELIVERY_UNKNOWN`.

No automatic mail retry was added. The test fixture repair does not change Notification response expectations or application behavior.

## 4. Repeated Identity and Notification test results

The final three consecutive runs used the same commit and unchanged 122-file source/test manifest. Before each run, ports 8081/8086/8025 were free and no Testcontainers were running. Testcontainers used disposable resources; an immediate post-run snapshot sometimes showed only the transient Ryuk cleanup container, which exited before the next run. No test service container or fixed-port conflict remained.

| Run | Started (+07:00) | Finished (+07:00) | Tests | Failures | Errors | Skipped | Evidence |
|---|---|---|---:|---:|---:|---:|---|
| 1 | 14:05:43 | 14:07:18 | 89 | 0 | 0 | 0 | [run-01](follow-up-runs/20261008T1405Z/run-01/) |
| 2 | 14:07:47 | 14:09:20 | 89 | 0 | 0 | 0 | [run-02](follow-up-runs/20261008T1405Z/run-02/) |
| 3 | 14:10:00 | 14:11:51 | 89 | 0 | 0 | 0 | [run-03](follow-up-runs/20261008T1405Z/run-03/) |

Each run folder contains a timestamped summary, redacted console log, and Surefire XML copied before the next Maven run. `system-out`/`system-err` and diagnostic payloads were removed from archived XML. The supplemental validation matrix passed 5/5, including actual wrong-method status lines, at [validation-matrix-supplemental](follow-up-runs/20261008T1405Z/validation-matrix-supplemental/).

`mvn -f notification-service/pom.xml verify` passed **23 tests, 0 failures, 0 errors, 0 skipped**. Its redacted Surefire XML is archived at [notification-verify-20261008T1340](follow-up-runs/notification-verify-20261008T1340/). The standalone Notification HTTP security pair passed 8/8; the standalone email-client fixture test passed 7/7.

The old audit's initial Identity run had 5 failures and 1 error, and the earlier detailed report had been overwritten before this follow-up. Its exact root cause remains **unknown**. The earlier audit's final 82/82 result and the three new 89/89 results mean the failure was **not reproduced in three new runs**; this does not prove the suite can never be flaky. Finding F-05 is not marked root-cause-resolved.

## 5. Disposable restart and recovery

The final recovery run used a unique Compose project, its own PostgreSQL volume, and local Mailpit. SMTP environment overrides were scoped to that process/Compose project. No real mailbox was contacted. The complete final run passed 54 checks; the detailed redacted result is in [disposable-recovery-summary.md](disposable-recovery-summary.md). Earlier runner attempts and their classification are preserved in [disposable-recovery-attempts.md](disposable-recovery-attempts.md).

Observed sequence and invariants:

1. Register A with forged client fields and B normally; A and B were pending `CUSTOMER` accounts. A's response contained no password/hash/code. Register messages went to Mailpit only.
2. Verify A; A became active and B stayed pending despite forged account/state fields. A's code could not verify B.
3. Login A and issue access/refresh tokens. Request Forgot A and leave the reset challenge pending. Keep B's registration challenge pending.
4. Restart Identity. The same public RSA JWK (`kid` and modulus) remained available. A's pre-restart access JWT authenticated after restart, A's refresh session rotated, B's verification OTP remained usable, and A's reset OTP remained usable. No migration repair or repeat application occurred.
5. Restart Notification. It returned healthy without sending an automatic duplicate. Missing/incorrect internal tokens sent no email. A correct internal token sent exactly one Mailpit message to the declared recipient; the unknown recipient field did not redirect it.
6. Verify B, create B's refresh session, and verify A's reset OTP to obtain a reset grant. Restart PostgreSQL using the same project container and named volume. It was healthy again in **5.7 seconds**; this is the observed duration, not an SLA. Identity/Notification became usable, and the same Flyway history remained `1:true,2:true,3:true`.
7. Reset A after database recovery. The transaction consumed the reset grant and revoked A's active refresh session. Old password was rejected; new password logged in. Reset-token replay failed. A's old refresh token failed; B's preexisting refresh token still worked. A's old access JWT remained accepted, matching the current expiry-only policy.

The final unique Compose project and its project-scoped volume were removed and verified absent. The two preliminary failed runner projects were also removed. Their locally built Docker images remain cached; no audit container, network, or data volume remains.

## 6. HMAC, default Compose, and email readiness

- The owner approved a new local HMAC key and accepted possible invalidation of the old challenge. `.env` now contains a non-placeholder Base64 value that decodes to 32 bytes. Compose passes the same setting to Identity. Neither value nor its digest is reported. This fixes the local configuration parsing issue without relaxing application validation.
- Before rotation, the permitted aggregate-only check found one `email_verification_challenge` row and zero `password_reset_challenge` rows in the persistent database. No row was read, deleted, or updated. The old registration challenge may no longer verify under the new key; resend is the recovery path if its account is still eligible.
- `docker compose config --format json` parsed successfully. The Identity private signing-key mount is read-only. The effective default SMTP target is **not Mailpit**. No normal `.env` email endpoint was called and no external message was sent.
- Because the default SMTP target is external/non-Mailpit, the requested safe default-Compose email smoke was **not run**. Default full local email readiness is **NOT READY / BLOCKED pending Mailpit configuration or another explicitly approved safe test setup**. The passing disposable stack does not change this status.
- External SMTP/provider delivery and production email readiness remain **NOT TESTED**.

## 7. Temp artifact cleanup

The exact old audit directory was resolved and checked as a normal directory with audit-only contents and no symlink/junction. A retry using native PowerShell with `LiteralPath` was still rejected by command safety review. No alternative shell or bypass was used.

**Still requires owner action:** delete only `C:\Users\FPTSHOP\AppData\Local\Temp\stylemind-identity-audit-20261008` after keeping any needed non-sensitive artifacts. That directory contains an old audit-only Compose override with one-off values; those values were not reused or reproduced. This is F-04 **BLOCKED**. The current disposable recovery projects and volumes were cleaned separately.

## 8. Hashes, working tree, and final data/container state

Identity migrations were byte-for-byte unchanged. The following SHA-256 values match before and after the final test/recovery work:

| Migration | SHA-256 |
|---|---|
| `V1__create_identity_tables.sql` | `937A0F0B96858F73E55B5C7D5B74A001AB4BD66723139FB5E043F12324E5CF25` |
| `V2__create_email_verification_challenge.sql` | `FC823A12C9B89F94ABD7E91A87906B841712DE310FC141EAD3DFB5846019703F` |
| `V3__create_password_reset_challenge.sql` | `57F226251E959E983213E014F6E279E176FCCCAADC4D350A06CA69E5A674BF48` |

See `follow-up-runs/20261008T1405Z/migration-sha256-before.txt` and `migration-sha256-after.txt`, plus the matching source/test manifest before/after files.

- `git diff --check` exited 0. Git emitted its existing LF-to-CRLF informational warnings for preexisting working-tree files.
- HEAD remained `b46bd5bd1d77af83edd3de5969c9cc707f7c183c` on `main`; no commit/push occurred.
- Persistent `stylemind-task4-postgres-1` remains stopped and `stylemind-task4_postgres-data` remains present. This audit did not start or write to that database. Its previously recorded V1–V3 history remains as documented in the prior report; this follow-up did not re-query the stopped persistent server.
- No containers are running from this audit. The final disposable project/volume and all Testcontainers services are absent. Unrelated containers and volumes were left alone.

## 9. Findings and readiness

| Finding | Status | Follow-up |
|---|---|---|
| F-01 — invalid local HMAC configuration | **RESOLVED for local configuration** | New owner-approved key parses and matches Compose. The preexisting challenge may be invalid; no challenge row was deleted. |
| F-02 — no repository IP/global email-abuse limit | **OPEN risk** | Not a demonstrated functional defect; edge controls were not assessed. Decide before public exposure. It is not a prerequisite to Manage Profile per the prior scope gate. |
| F-03 — workbook backlog still says To Do | **OPEN** | Workbook intentionally unchanged. |
| F-04 — old audit temp artifact directory | **BLOCKED** | Exact deletion action remains with the owner; no bypass. |
| F-05 — earlier one-off Identity test failures | **NOT REPRODUCED** | Three final 89-test runs are green. Original root cause remains unknown; no claim of permanent flake resolution. |
| Internal Notification path/auth concern | **NOT REPRODUCED** | Embedded HTTP tests and disposable real Notification/Mailpit flow deny unauthorized requests and send only for the authenticated contract route. |
| Email maximum length | **PROPOSED / TBD** | Current 255-character Register email is accepted. No maximum was specified in DTO/schema; product owner may decide whether to add one later. No change made. |

### Separate readiness conclusions

**A. US-02.01–US-02.04 functional status:** retain the prior report's implemented/pass-in-tested-scope statuses. This follow-up did not downgrade those stories or add business behavior.

**B. Automated regression:** **PASS with bounded confidence.** Identity `verify`: 89/89, three consecutive runs; Notification `verify`: 23/23; no skips. F-05 is not reproduced, not root-cause-resolved.

**C. Default local runtime readiness:** **NOT READY for a safe full email smoke** because default `.env` SMTP is not Mailpit. Compose config validates and HMAC/key mount are correct, but default endpoints were not exercised.

**D. External email readiness:** **NOT TESTED.** No external SMTP send occurred in this follow-up.

**E. Production readiness:** **NOT READY / NOT ASSESSED.** Production topology, provider delivery, key operations, and edge abuse controls remain outside this local audit.

**F. Continue to Manage Profile:** **Yes, using the owner-approved disposable Mailpit development setup.** The required Identity regression and restart/recovery evidence is green, and no P0/P1 functional defect was found. Keep default `.env` full email readiness, external SMTP, F-02, and the separate F-04 cleanup status visible; this conclusion does not label the workstation's default SMTP configuration ready or certify production.
