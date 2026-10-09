# Current Business Rules

This is the concise owner-approved Identity rule set for current implementation work. The approvals were stated in the project owner's Register, Login and Forgot Password instructions, including the separate explicit V3/session-policy approval; no new DEC identifier is created here. The local code, migrations and [Identity audit](../qa/identity-audit/20261008T1144-0700/IDENTITY_RELEASE_READINESS_AUDIT.md) are implementation/test evidence, not the source of owner approval. Unknown behavior stays TBD as listed in [open implementation decisions](OPEN_IMPLEMENTATION_DECISIONS.md).

## Register and email verification — US-02.01

**CONFIRMED owner rules:**

- Register creates an account with `PENDING_VERIFICATION`, assigns `CUSTOMER` server-side and creates its email-verification challenge in the same Identity database transaction. Client input cannot choose account status, roles, IDs or verification state. Do not issue access or refresh tokens from Register.
- Normalize email consistently by trimming and lowercasing with `Locale.ROOT`; never trim or normalize passwords. Store only a password hash, never plaintext. The password-creation policy is applied at account creation, not as a newly invented Login rule.
- A successful email verification consumes the current challenge, transitions `PENDING_VERIFICATION` to `ACTIVE`, and sets `email_verified_at`; that timestamp is not OTP storage.
- Verification code is a six-digit string generated with `SecureRandom`; leading zeroes are valid. TTL is five minutes, with `now >= expires_at` treated as expired. Store only a purpose/context-bound HMAC-SHA-256 digest over Identity/account/challenge context using a secret outside the database; compare digests in constant time.
- A well-formed wrong OTP increments the persistent challenge counter. The fifth failed attempt locks that challenge for 15 minutes. While locked, both verify and resend are denied, even for a correct code. OTP lock is not an account lock and does not affect Login.
- Before the fifth failure, resend does not reset the wrong-attempt count. After the lock expires, the old code stays unusable; a new resend may reset failed attempts/lock. An already-active account cannot receive a new verification challenge.
- The initial email counts as send one. Enforce a 60-second cooldown and at most three send attempts in a fixed one-hour window. Failed/uncertain sends count; the fixed window restarts after it elapses. Account/challenge row locks serialize verification and resend.
- Commit challenge state before the HTTP call to Notification; never call SMTP while the Identity database transaction/row lock is open. If the post-commit delivery fails or is unknown, keep the pending account and provide the implemented resend recovery action. Do not tell the user to register again.

The rule values above are supported by the owner's Register approval and the observed V2 schema, source and audit tests. Current details are documented in [Identity README](../../identity-service/README.md), [V2](../../identity-service/src/main/resources/db/migration/V2__create_email_verification_challenge.sql) and the audit.

## Login — US-02.02

**CONFIRMED owner rules:**

- `POST /api/v1/auth/login` accepts email and password. Email is normalized like Register. Password is required and preserved byte-for-byte as supplied; Login does not apply the account-creation minimum. The approved maximum is 72 UTF-8 bytes for the existing BCrypt-compatible storage path.
- Unknown email and wrong password use the same `401 INVALID_CREDENTIALS` response. Check account state only after the password matches. Only `account_status = ACTIVE`, non-null `email_verified_at`, and at least one allowed role in `account_role` may receive tokens. Every other account status is denied. OTP challenge lock is separate from account status.
- Do not accept role/status/subject claims from the request. Do not invent role defaults, a login-attempt counter or an account lock/unlock lifecycle. `LOCKED` is only a test fixture proving a non-`ACTIVE` status is denied.
- Access JWT uses RS256. Claims are UUID `sub`, configured `iss` and `aud`, `iat`, `exp`, UUID `jti`, and roles sourced from `account_role`. Identity validates signature/algorithm, expiry/issued-at, issuer, audience, UUID subject/token ID, and maps known database roles to `ROLE_CUSTOMER` / `ROLE_ADMIN`. Defaults: issuer `https://identity.stylemind.local`, audience `stylemind-api`, 15-minute access TTL and 7-day refresh TTL. JWKS is `GET /.well-known/jwks.json`; the Compose consumer URL `http://identity-service:8081/.well-known/jwks.json` is not the issuer identifier.
- Signing key is an externally supplied, stable RSA private key (minimum 2048 bits); never generate a new production key on each start, commit or log a private key. Do not put email/password/hash/OTP/refresh token into JWT claims.
- Each successful Login creates a new refresh session. Refresh token is an opaque token from at least 32 random bytes, Base64URL without padding; Identity stores only SHA-256 hex digest in the existing `refresh_session.token_hash`. Do not store plaintext and do not revoke older sessions on Login.

These are explicit Login-contract approval rules; current Identity implementation and local tests corroborate them. Other resource servers have not been updated to the same issuer/audience/role-authority validation contract.

## Refresh and Logout — US-02.03

**CONFIRMED owner rules:**

- Refresh rotates one opaque token/session at a time and uses the existing `refresh_session` columns only. A successful rotation revokes the presented row and inserts a new digest-only row transactionally. Recheck account and session under the existing lock order; do not hold a row lock while running BCrypt.
- Rotation preserves the original absolute `expires_at`; it does not extend the refresh lifetime. Concurrent reuse of one token allows only one successful rotation. Do not revoke unrelated sessions or add token-family/device behavior.
- Logout is idempotent and revokes only the session identified by its refresh token. It does not delete that row or revoke already-issued access JWTs. Existing access JWTs remain valid until `exp`; no blacklist or immediate invalidation is added.

The behavior is stated in the owner-approved Login/Refresh task history and evidenced by the current source/audit. Do not expand US-02.03 during unrelated stories.

## Forgot Password — US-02.04

**CONFIRMED owner rules:**

- Only verified `ACTIVE` accounts are eligible to receive/use a reset challenge. For syntactically valid email input, the public request response is generic `202`; it must not reveal account existence/eligibility. No account/password/session state changes during the OTP request.
- `202` acknowledges the generic request; it does not guarantee inbox delivery. Unknown/ineligible accounts, rate-limited/locked challenges and requests while an unexpired verified grant is usable send no new message. A new request must not invalidate a still-live grant. After the grant expires or is consumed, a new challenge may start within the same send/attempt limits.
- Use `password_reset_challenge` (V3) separately from `email_verification_challenge` (V2). Never reuse the verification OTP table, `refresh_session`, Redis or another field for password-reset OTP state.
- Reset OTP is a six-digit `SecureRandom` string, can start with zero, and expires after five minutes (`now >= otp_expires_at` is expired). Store a purpose-bound HMAC-SHA-256 digest only. A fifth wrong formatted attempt locks the reset challenge for 15 minutes; cooldown is 60 seconds; at most three sends per fixed hour; failed/timeout delivery counts. A live verified reset grant is not destroyed merely because another request arrives.
- Successful OTP verification issues an opaque random reset token of at least 32 bytes, Base64URL without padding, expiring after five minutes. Store only its SHA-256 digest. The grant is one-use. Requesting/verifying a reset OTP does not change the password, account status or sessions.
- On successful password reset, in one Identity database transaction update `account.password_hash`, mark the grant used, and revoke every currently unrevoked `refresh_session` for that account. A failure rolls all these changes back. This all-session revocation rule applies only to successful reset; Logout remains single-session. Previously issued access JWTs remain valid until expiry. Reset does not activate the account or issue tokens.

V3 was explicitly approved, and the owner separately approved the reset transaction/session policy. See [V3](../../identity-service/src/main/resources/db/migration/V3__create_password_reset_challenge.sql), [Identity README](../../identity-service/README.md), and the audit. Do not edit V1/V2 or change this lifecycle.

## Manage Profile — US-02.05

**CONFIRMED owner rules:**

- The exact routes are `GET/PATCH /api/v1/me` and `GET/PUT /api/v1/me/style-profile`. All require JWT and `CUSTOMER` or `ADMIN`; both roles may operate only on the JWT `sub`. Never accept an account owner from request path, query or body. These checks do not add a global account-state gate.
- `GET /me` returns account ID, email and either `profile: null` or `profile.display_name`. It never writes. `PATCH /me` accepts only a present, non-null `display_name`, Unicode-trims it, and creates/updates only the user's profile row. Missing, null, blank or unknown fields are invalid. Register does not create a placeholder.
- `GET /me/style-profile` returns `style_profile: null` when absent. A present profile includes all five nullable scalar fields and all three arrays; arrays are stably sorted and reads do not write.
- `PUT /me/style-profile` is full replacement and requires all eight fields. The five scalars may be null; each collection is required and non-null, and `[]` clears it. Unknown properties and duplicate canonical values are invalid. Empty scalars/collections may create an empty parent row. The scalar and all three collections update in one transaction.
- A valid JWT whose subject account no longer exists receives `404 RESOURCE_NOT_FOUND`. No profile request changes ACCOUNT or ACCOUNT_ROLE. Writes lock the owner account first; concurrent writes serialize and the last serialized writer wins. Failed writes roll the aggregate back.
- `display_name` is Unicode-trimmed, 1–100 Unicode code points after trim, and rejects control characters. It preserves Vietnamese and other remaining characters.
- `fit_preference`, `style_codes`, and `color_codes` use trimmed syntax `^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$`; code values canonicalize with `Locale.ROOT` uppercase. No finite vocabulary is used. Code collections allow up to 50 non-null unique canonical values.
- `height_cm` is a nullable exact JSON decimal from 50 through 250 cm inclusive, with at most one fractional digit. No float conversion or rounding is allowed. The three nullable size labels are Unicode-trimmed free-form values of 1–32 code points and reject controls; no size-system enum is added.
- `brand_ids` accepts up to 50 unique, hyphenated UUIDs (hex case-insensitive), and persists them as logical Commerce references. Identity does not call Commerce or verify existence/active status. A syntactically valid UUID is not evidence that a brand exists or is active.
- The implementation uses existing V1 tables only: `account`, `user_profile`, `style_profile`, `profile_style`, `profile_color`, and `profile_brand`. No migration, column, FK, table or business API is added.

This approval is scoped to US-02.05. Historical `OPEN-*` entries retain their original archival meaning; this rule set does not globally close or rewrite unrelated questions. See the [US-02.05 implementation report](../qa/identity-profile/US-02.05_IMPLEMENTATION_REPORT.md) for local verification evidence when complete.

## Manage Addresses — US-02.06

**CONFIRMED owner rules from the US-02.06 implementation instruction:**

- All eight Address/location APIs require a valid JWT with `CUSTOMER` or `ADMIN`. Address ownership is self-only and comes exclusively from JWT `sub`; both roles are self-only. A missing subject account returns 404. A foreign or missing address shares `404 RESOURCE_NOT_FOUND`. Do not add a new account-status gate.
- Use only the existing `public.customer_address` columns from V1. Do not add or edit migrations/schema. Every write locks the matching `public.account` row first, checks the current owner state, and applies changes in one database transaction. Account with no addresses has no default; first created address is default, later addresses are not. Writes through this service preserve exactly one default while addresses exist. This is an application invariant only, not a database constraint and not protection against direct SQL/other writers. Do not repair anomalous legacy data.
- POST and PATCH accept only `label`, `recipient_name`, `recipient_phone`, `address_line`, `ward_code`. PATCH merges supplied fields after the owner lock; omitted fields stay unchanged, explicit null clears only `label`/`ward_code`, and `{}` is invalid. Owner/default/role/status fields and all unknown properties are rejected. Phone is validated as a Vietnamese number and stored as E.164. Supplied ward codes must be exact strings from the pinned local directory; codes remain strings. Address text is Unicode-trimmed and bounded by the story contract.
- `GET /api/v1/me/addresses` sorts default first then UUID; detail is owner-scoped. `POST` returns 201/Location. `PUT /api/v1/me/addresses/{address_id}/default` atomically switches the default and is repeatable. Deleting a default with alternatives returns `409 DEFAULT_ADDRESS_CHANGE_REQUIRED`; deletion never silently selects a replacement. Deleting a non-default or the sole address is allowed.
- Lookup uses a pinned local projection of the Vietnam administrative dataset; no runtime network lookup, map/geocoding, address rewriting or automatic code remapping. Do not send email/SMS/OTP, verify phone ownership, call Commerce/Order, add deduplication/idempotency, soft deletion, history or audit data. Identical POST content may create separate addresses.
- Address responses expose only address response fields, include nullable values, and use `Cache-Control: no-store`. Do not log address/phone values or secrets. The exact implementation contract and local verification state are in [the US-02.06 contract](../planning/identity/US-02.06_MANAGE_ADDRESSES_CONTRACT.md) and [implementation report](../qa/identity-address/US-02.06_IMPLEMENTATION_REPORT.md). These rules are scoped to this story and do not close unrelated `OPEN-*` decisions.

## Notification delivery contract

**CONFIRMED owner rules:**

- Identity calls only the fixed internal Notification endpoints for verification/reset email; Notification authenticates the internal service token, uses fixed templates and does not store challenge data or connect to Identity's database.
- Only HTTP `202` is the agreed acceptance response. A `503` is a definite `EMAIL_DELIVERY_FAILED`; timeout or any unexpected response (including other 2xx codes) is `EMAIL_DELIVERY_UNKNOWN`. Do not retry an uncertain email automatically.
- Do not log OTP, request/response bodies carrying OTPs, authorization/service tokens, SMTP credentials or other secrets. No email delivery guarantee means inbox placement.
- Mailpit is the local capture/test SMTP target. External SMTP/provider delivery and production email operations are unverified; do not select a production provider or infer its active configuration from documentation.

The internal auth/202 behavior was explicitly requested in the Register final review and is covered by Notification tests plus the Identity audit follow-up. See [Notification README](../../notification-service/README.md).
