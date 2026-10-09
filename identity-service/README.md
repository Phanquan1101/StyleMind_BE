# Identity — EP-02 / Register, Login, Refresh/Logout, Forgot Password and Profile

US-02.01 Register, US-02.02 Login, US-02.03 Refresh/Logout, US-02.04 Forgot Password, and US-02.05 Manage Profile are implemented in this repository's local Identity scope. Register includes transactional account/role/challenge creation, SMTP email verification, resend limits, expiry, temporary OTP lockout, and PostgreSQL-backed integration tests. Login validates credentials and eligibility, signs access JWTs, and stores opaque refresh-token digests in `refresh_session`. Refresh rotates one refresh session at a time; Logout idempotently revokes only the session identified by its supplied refresh token. Forgot Password uses a separate challenge and changes the password transactionally with revocation of all active refresh sessions. Manage Profile reads and updates only the JWT-subject account using existing V1 tables. This is local scope evidence, not a production-readiness claim.

## Status and scope

The implementation and local verification status for US-02.01–US-02.04 is supported by the [Identity release audit](../docs/qa/identity-audit/20261008T1144-0700/IDENTITY_RELEASE_READINESS_AUDIT.md) and [follow-up](../docs/qa/identity-audit/20261008T1144-0700/IDENTITY_AUDIT_FOLLOW_UP.md). US-02.05 Manage Profile follows the owner's explicit A/B/C approval and its verification evidence is in the [implementation report](../docs/qa/identity-profile/US-02.05_IMPLEMENTATION_REPORT.md). US-02.06 Manage Addresses is **DONE in local scope** from the story-specific owner contract; see the [contract](../docs/planning/identity/US-02.06_MANAGE_ADDRESSES_CONTRACT.md), [Postman collection](../postman/StyleMind-Identity-Manage-Addresses.postman_collection.json), [Postman run guide](../postman/README.md#manage-addresses--us-0206) and [implementation report](../docs/qa/identity-address/US-02.06_IMPLEMENTATION_REPORT.md). US-02.07 Manage Users has a separate [owner-approved contract](../docs/planning/identity/US-02.07_MANAGE_USERS_CONTRACT.md), [Postman collection](../postman/StyleMind-Identity-Manage-Users.postman_collection.json), and [implementation report](../docs/qa/identity-users/US-02.07_IMPLEMENTATION_REPORT.md). Workbook rows remain `To Do`; the workbook is not changed. EP-02 status is summarized in the US-02.07 report and is not a production-readiness statement.

The current decision labels and open profile questions are maintained in the [project context](../docs/governance/CURRENT_PROJECT_CONTEXT.md), [business rules](../docs/governance/CURRENT_BUSINESS_RULES.md) and [open implementation decisions](../docs/governance/OPEN_IMPLEMENTATION_DECISIONS.md). This README describes the current local implementation contract; the source of approval is the project owner's explicit instruction history, not code behavior alone.

## Public API

Only the exact `POST` routes listed here are public without a JWT. Other `/api/**` routes still require JWT authentication.

| Route | Request | Success | Behavior |
| --- | --- | --- | --- |
| `POST /api/v1/auth/login` | `email`, `password` | `200` with `access_token`, `refresh_token`, `token_type`, `expires_in` | Normalizes email like Register; preserves the password; issues tokens only for verified `ACTIVE` accounts with at least one allowed database role. Returns `Cache-Control: no-store`. |
| `POST /api/v1/auth/refresh` | `refresh_token` | `200` with new `access_token`, rotated `refresh_token`, `token_type`, `expires_in` | Uses the opaque refresh token without requiring an access JWT. Each token rotates once; the new session keeps the old absolute `expires_at`. Invalid, expired, revoked and replayed tokens share `401 INVALID_REFRESH_TOKEN`. Returns `Cache-Control: no-store`. |
| `POST /api/v1/auth/logout` | `refresh_token` | `204 No Content` | Idempotently revokes only the identified session, including expired sessions; a well-formed unknown token also returns `204`. Returns `Cache-Control: no-store`. |
| `POST /api/v1/auth/register` | `email`, `password` | `201` with `account_id`, `email`, `account_status` | Creates account, `CUSTOMER` role and verification challenge in one transaction, then calls Notification. `201` is returned after SMTP acceptance. |
| `POST /api/v1/auth/verify-email` | `email`, six-digit string `code` | `204` | Consumes the current challenge and changes `PENDING_VERIFICATION` to `ACTIVE` while setting `email_verified_at`. No token is issued. |
| `POST /api/v1/auth/resend-email-verification` | `email` | `204` after SMTP accepts | Rotates the current challenge and sends a new code. Password and roles are unchanged. |
| `POST /api/v1/auth/forgot-password` | `email` | `202` with a generic message | Sends an OTP only for a verified ACTIVE account; response content does not disclose eligibility. Delivery errors and rate limits retain the same public response. |
| `POST /api/v1/auth/verify-password-reset` | `email`, six-digit string `code` | `200` with `reset_token`, `expires_in` | Verifies the reset OTP once and returns an opaque five-minute, one-use token. |
| `POST /api/v1/auth/reset-password` | `reset_token`, `new_password` | `204 No Content` | Changes the password and revokes all unrevoked refresh sessions in one transaction. Does not issue JWTs. |

### Manage Profile — US-02.05

All four profile routes require a valid bearer JWT with `CUSTOMER` or `ADMIN` authority. Every operation uses only the UUID in JWT `sub`; query/path/body values cannot select another account. A missing account for an otherwise valid subject returns `404 RESOURCE_NOT_FOUND`.

| Route | Request | Success | Behavior |
| --- | --- | --- | --- |
| `GET /api/v1/me` | None | `200` with `account_id`, `email`, `profile` | Read-only. `profile` is null until the user has a `user_profile` row; otherwise it contains `display_name`. |
| `PATCH /api/v1/me` | `display_name` only | `200` with the GET shape | Required, non-null and nonblank; trimmed at Unicode whitespace boundaries, 1–100 code points, no controls. Creates/updates only `user_profile.display_name`. |
| `GET /api/v1/me/style-profile` | None | `200` with `style_profile` | Read-only. Null when the parent is absent; otherwise contains all five nullable scalars and the three sorted arrays. |
| `PUT /api/v1/me/style-profile` | All eight style-profile fields | `200` with the GET style shape | Full replacement. Five scalars may be null; three arrays are required and non-null (`[]` clears). All changes are atomic. |

Fit/style/color codes are syntax-validated using `^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$`, trimmed and stored uppercase; no finite vocabulary is defined. Height is an exact decimal number from 50 through 250 cm with at most one fractional digit. Optional size labels are trimmed free-form text of 1–32 code points. Each collection allows up to 50 unique values. Unknown fields, wrong JSON types, omitted required fields and canonical duplicates are rejected with `400 INVALID_REQUEST`.

Brand preferences accept hyphenated UUIDs and are stored in `profile_brand.brand_id` as logical Commerce references only. Identity does not call Commerce or verify that a UUID exists or is active. Valid syntax is not existence verification. Profile APIs use existing V1 tables only; no migration or cross-service FK is added. Writes lock the account row first, serialize concurrent updates, and replace all style collections in one transaction. Register does not create profile placeholders.

### Manage Addresses — US-02.06

The Address API is implemented in local scope under the explicit US-02.06 contract. All routes require a signed JWT with `CUSTOMER` or `ADMIN`; Address ownership is always the JWT `sub` and remains self-only for both roles. Identity uses the existing `public.customer_address` table only. It does not add a migration or schema object. Address writes lock the owning `public.account` row and update under one transaction. The unique-default behavior is an application invariant for writes through this service, not a database constraint and not protection from direct SQL or another writer.

| Route | Behavior |
| --- | --- |
| `GET /api/v1/me/addresses` | Owner-only list, default first then UUID. |
| `GET /api/v1/me/addresses/{address_id}` | Owner-only detail; foreign and missing addresses share 404. |
| `POST /api/v1/me/addresses` | Create; first address becomes default; returns 201 and Location. |
| `PATCH /api/v1/me/addresses/{address_id}` | Merge supplied fields under the owner lock; nullable label/ward can be cleared. |
| `PUT /api/v1/me/addresses/{address_id}/default` | Atomically select the default; repeat is successful. |
| `DELETE /api/v1/me/addresses/{address_id}` | Deletes non-default or sole address; default with alternatives returns 409. |
| `GET /api/v1/locations/provinces` | Authenticated lookup from pinned local reference data. |
| `GET /api/v1/locations/provinces/{province_code}/wards` | Authenticated wards lookup scoped to a known province. |

The pinned dataset is v5.2.0 and has no runtime network fallback. Vietnam phone values are validated locally with pinned libphonenumber and stored in E.164 form. Address responses use `Cache-Control: no-store` and contain no account/credential/session fields. No email/SMS/OTP, map, checkout, Commerce call, soft delete, or idempotency behavior is part of this story. Identical POSTs may create separate rows. US-02.06 is DONE in local scope; evidence and limits are in the implementation report.

Register retains its existing response fields. If SMTP reports a definite failure, Register returns `503 EMAIL_DELIVERY_FAILED`; if the result is unknown, it returns `504 EMAIL_DELIVERY_UNKNOWN`. Those Problem Details include `account_created: true`, `account_status: PENDING_VERIFICATION`, and `next_action: RESEND_EMAIL_VERIFICATION`. The committed account is retained. Resend delivery failures return the same delivery code without the Register-only `account_created` flag.

### Manage Users — US-02.07

These three routes require an ADMIN access-token claim and revalidate the actor against Identity `account` and `account_role` on each request. The actor must still exist, be ACTIVE, verified, and hold ADMIN. This live-state requirement applies only to Manage Users.

| Route | Behavior |
| --- | --- |
| `GET /api/v1/admin/users` | Stable, paginated user list with optional status, role, and literal email substring filters. Returns the complete sorted role set and safe account fields. |
| `PATCH /api/v1/admin/users/{account_id}/status` | Allows ACTIVE↔SUSPENDED only; pending activation and unverified reactivation are rejected. |
| `PUT /api/v1/admin/users/{account_id}/roles` | Replaces the full nonempty set of CUSTOMER and/or ADMIN roles. |

Actual self status/role changes and removal/suspension of the last qualifying Admin are rejected. No-op self requests are allowed without row writes. Status/role changes do not revoke refresh sessions or invalidate issued access JWTs; Login/Refresh use current database status/roles, while Manage Users performs its own live actor check. Responses use `Cache-Control: no-store`. The service uses only current Identity tables and adds no migration. See the [contract](../docs/planning/identity/US-02.07_MANAGE_USERS_CONTRACT.md), [Postman run guide](../postman/README.md#manage-users--us-0207), and [report](../docs/qa/identity-users/US-02.07_IMPLEMENTATION_REPORT.md).

Login input errors return `400 INVALID_REQUEST`. Unknown email and incorrect password share `401 INVALID_CREDENTIALS`; after correct credentials, any account other than verified `ACTIVE`, or an account with no allowed `account_role`, returns `403 ACCOUNT_NOT_ACTIVE`. OTP challenge lock state is not consulted for Login. Stored password hashes that cannot be verified fail closed with a sanitized `500 INTERNAL_ERROR`.

The access token is RS256 with `sub` account UUID, configured `iss`/`aud`, `iat`, `exp`, UUID `jti`, and roles copied from `account_role`. The decoder validates RS256 signature, required expiry/issued-at, issuer, audience, UUID subject and UUID token ID. `roles` map to `ROLE_CUSTOMER` and `ROLE_ADMIN`; absent or unknown roles grant no role authority. Password policy validation is applied at registration only. Login accepts passwords up to 72 UTF-8 bytes, preserving all supplied characters.

Each successful Login creates a new `refresh_session` row using the existing columns. The client receives a 32-byte-entropy Base64URL refresh token; PostgreSQL stores only its SHA-256 hex digest in `token_hash`. Login does not revoke earlier sessions.

### Refresh and logout behavior

- Refresh validates the exact unpadded Base64URL encoding of 32 random bytes, looks up only its SHA-256 digest, and rechecks the session under lock. Account is locked before the refresh-session row. A successful rotation sets `revoked_at` on the old row and inserts a new row in one transaction; signing, update, insert or commit failure returns no tokens and rolls back.
- Rotation copies the previous row's `expires_at` exactly. It does not extend the refresh-session lifetime. The new row's `issued_at` is the rotation time. The access JWT still receives its configured access TTL (15 minutes by default), so an access JWT can remain valid after its refresh session expires or is revoked.
- Concurrent refresh requests with one token serialize on the account row; one can rotate and the other receives `401 INVALID_REFRESH_TOKEN`. A consumed token cannot rotate again. Replay does not revoke unrelated sessions and there is no token-family replay detection.
- Logout does not require an active or verified account. It is idempotent, preserves the first `revoked_at`, and does not remove the row. A revoked token from before rotation cannot revoke the new session; the client must use the newest refresh token to revoke the current session.
- Logout revokes refresh-session use only. Already issued access JWTs remain accepted by the current decoder until their own expiry; this story adds no blacklist or immediate access-token revocation. There is no all-device logout or token-family behavior.
- Both endpoints return `Cache-Control: no-store` on successful and error responses. They do not log request tokens or stored digests. An access JWT is not a valid refresh token.

## Signing key and JWKS

Identity reads an RSA PKCS#8 PEM private key through `STYLEMIND_JWT_PRIVATE_KEY_PATH`. RSA keys must be at least 2048 bits. The same stable key is used to verify Identity access tokens locally; Identity does not fetch its own JWKS over HTTP. Missing or invalid key material and invalid TTL configuration stop startup. The public JWKS endpoint is `GET /.well-known/jwks.json`; it exposes only public RSA parameters and uses a `kid` derived from the public-key thumbprint.

Identity's decoder now uses the public key derived from its configured private key, and pins RS256 plus the configured issuer and audience. Identity no longer uses `STYLEMIND_JWT_JWK_SET_URI` or `STYLEMIND_JWT_JWS_ALGORITHM` to choose its own decoder. The other resource servers retain their existing JWK URI and algorithm settings; their issuer/audience validation and role-authority conversion have not been implemented by this story.

The local Compose setup expects a private key outside source control. Copy `.env.example` to `.env`, create `.secrets/stylemind-identity-private.pem` with a local-only key, and keep `.secrets/` ignored. Compose mounts the file read-only at `/run/secrets/stylemind-identity-private.pem`. For example, from the repository root, OpenSSL can create a PKCS#8 key with:

```powershell
New-Item -ItemType Directory -Force .secrets | Out-Null
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out .secrets/stylemind-identity-private.pem
```

The configured issuer identifier defaults to `https://identity.stylemind.local`; the audience defaults to `stylemind-api`. In Compose, a consumer retrieves signing keys from `http://identity-service:8081/.well-known/jwks.json`. This network URL is separate from the issuer claim. Other resource servers are not updated by US-02.02: their issuer/audience validation and `roles` authority conversion remain outside this Identity change. This local setup does not define production topology.

Verification errors use sanitized Problem Details: `INVALID_REQUEST`, `EMAIL_VERIFICATION_INVALID`, `EMAIL_VERIFICATION_EXPIRED`, `EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED`, `EMAIL_ALREADY_VERIFIED`, and `EMAIL_VERIFICATION_NOT_ALLOWED`. Resend also uses `EMAIL_VERIFICATION_SEND_LIMIT_EXCEEDED`, `EMAIL_DELIVERY_FAILED`, and `EMAIL_DELIVERY_UNKNOWN`. Active lockouts and resend limits include `Retry-After`. Responses never include the OTP, its digest, remaining attempts, account data, password, or tokens.

## Challenge rules

- OTP is a six-character decimal string generated with `SecureRandom`; leading zeroes are preserved. It expires after five minutes. `now >= expires_at` is expired.
- PostgreSQL stores only HMAC-SHA-256 over account ID, challenge ID and OTP. The key is base64 supplied through `STYLEMIND_VERIFICATION_HMAC_SECRET`; it must decode to at least 32 bytes. Comparison uses `MessageDigest.isEqual`.
- A correctly formatted wrong code increments the persisted counter. The fifth wrong code sets a 15-minute lock. Verify and resend are blocked during the lock, including a correct code. After the lock expires the old challenge remains unusable until resend.
- A resend before lockout does not reset failed attempts. Resend after the lock ends can reset the attempts and lock. Each allowed resend rotates the challenge ID and digest.
- The initial email counts as send one. Cooldown is 60 seconds; no more than three attempts are reserved within a fixed one-hour window. SMTP failure and timeout still count. Row locks serialize these rules across Identity instances.
- Account is locked before its challenge for both verify and resend. Database transactions finish before the Notification HTTP request begins. A process crash between commit and send can leave a pending account; resend is the recovery path. Delivery is not exactly-once.

## Forgot Password

Email is trimmed and lowercased like Register/Login. New passwords retain the exact submitted characters and use the existing 12-code-point minimum and 72-byte UTF-8 maximum. Only verified `ACTIVE` accounts can receive or use a reset challenge. A valid email always gets the same `202` message, including unknown, pending, rate-limited and delivery-failure cases. Delivery is synchronous, so response timing can still vary; this flow does not claim to eliminate timing-based account enumeration.

Forgot Password uses only `public.password_reset_challenge`; it does not read or write `email_verification_challenge`. OTPs are six-digit `SecureRandom` values with five-minute expiry; PostgreSQL stores only a purpose-bound HMAC-SHA-256 digest. A 60-second cooldown, three sends per fixed hour, five wrong-attempt limit and 15-minute challenge lock apply only to password reset. SMTP failures count as a send attempt. A valid reset grant is not invalidated by another forgot-password request. Reset tokens contain 32 random bytes in unpadded Base64URL form, expire after five minutes, and are stored only as SHA-256 byte digests.

Password encoding occurs before the reset transaction takes row locks. The transaction locks the account, then the challenge, revalidates both, updates `account.password_hash`, marks the grant consumed, and revokes every still-active `refresh_session` row. A failure rolls back all three changes. Requesting or verifying OTP does not revoke sessions. Logout still revokes only its supplied session. Previously issued access JWTs remain usable until `exp`; no blacklist or immediate access-token revocation is added. Reset never signs in the account.

## Schema and migrations

V1 and V2 are preserved byte-for-byte. V1's SHA-256 is `937A0F0B96858F73E55B5C7D5B74A001AB4BD66723139FB5E043F12324E5CF25`; V2's SHA-256 is `FC823A12C9B89F94ABD7E91A87906B841712DE310FC141EAD3DFB5846019703F`. V2 creates only `public.email_verification_challenge`; V3 creates only the separate `public.password_reset_challenge` with an Identity-local account FK, fixed-length digests, bounded counters, lock/grant checks, and a unique nullable reset-token hash. No cross-service FK is added. No field in V1, including `email_verified_at`, stores OTP data.

Flyway runs the migrations against the configured Identity datasource. Identity Testcontainers suites apply V1, V2 and V3 to disposable PostgreSQL 18.6 databases; they do not clean the local Compose database/volume. Production topology remains TBD.

## Notification and local configuration

Notification exposes `POST /internal/v1/email-verifications` and `POST /internal/v1/password-resets`, each protected by the shared `X-StyleMind-Service-Token`. The new reset route accepts only `recipientEmail` and `resetCode` and sends a fixed password-reset template through SMTP. Notification has no challenge logic or Identity database access and returns `202` only after SMTP accepts the message. The token is a local service-to-service choice; both services require at least 32 bytes and compare it in constant time. This does not define production service authentication.

SMTP host, credentials, TLS/auth flags, and finite connect/read/write timeouts are environment-configured. Local Compose defaults to Mailpit for capture. The owner previously chose Mailjet for local workstation SMTP; that choice does not establish production provider approval or prove external delivery. The current `.env` is user-managed and was not inspected in this documentation reconciliation. Keep real credentials out of this repository. End-to-end tests use Mailpit and do not send live Mailjet email. Copy `.env.example` to `.env` and replace placeholders, including a fresh base64 HMAC key and service token. Never test with a real customer address.

## Verification

Run from the repository root with Docker available. Build Notification first because Identity's E2E suite launches the resulting real service artifact:

```powershell
mvn -f identity-service/pom.xml verify
git diff --check
```

Identity integration tests use PostgreSQL 18.6 Testcontainers, launch the actual Notification service artifact in a separate container, and send through Mailpit SMTP. They retrieve the message from Mailpit's local test mailbox to verify and exercise real flows; they do not use a mocked sender for email acceptance. Mailpit's Chaos feature returns a real SMTP rejection for the delivery-failure and resend-recovery case. No test is configured to skip when Docker is unavailable.

The coverage includes valid registration/verification, normalized duplicate email, duplicate verification, persisted wrong attempts and fifth-attempt OTP lock, lock behavior and recovery, exact expiry boundary, malformed OTP without attempt increment, leading-zero and account/challenge-bound HMAC, challenge rotation, cooldown/window rollover, failed-send accounting, transaction rollback, concurrent verify and verify/resend, protected Notification route, and sensitive-value redaction. Login integration tests use a separate disposable PostgreSQL 18.6 container and exercise valid signed-token issuance, password/state/role checks, OTP-lock independence, race revalidation, independent refresh sessions, rollback on signing/persistence failures, public JWKS verification over HTTP, and Register/verify/Login as one flow. Refresh/logout integration tests use a separate PostgreSQL 18.6 container and real HTTP requests; they cover absolute expiry, token rotation/replay, account state and current role checks, idempotent logout, access-JWT lifetime, signing/persistence rollback, and concurrent Refresh/Logout. `LOCKED` exists only as a disposable database test fixture for rejecting every non-`ACTIVE` status; it is not a production status-management feature.
