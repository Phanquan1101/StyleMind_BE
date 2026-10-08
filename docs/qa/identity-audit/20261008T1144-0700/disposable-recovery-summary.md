# Disposable restart and recovery audit

- Compose project: stylemind-identity-audit-20261008-586ce25b
- Started: 2026-10-08T13:59:20.421+07:00
- Finished: 2026-10-08T14:00:32.964+07:00
- Result stage: reset-password-after-postgres-recovery
- Result: PASS
- Failure type: none
- Cleanup: exact disposable Compose project and volume removed
- PostgreSQL recovery: 5.7 seconds

## Checks

- PASS: Default .env SMTP target was inspected without printing it; external/non-Mailpit configuration remains isolated from this run
- PASS: Local HMAC is valid Base64 and decodes to 32 bytes
- PASS: Database administrator username is configured
- PASS: Internal service token is configured
- PASS: Disposable host ports 8081/18086/8025 are available
- PASS: Unique Compose project has no pre-existing containers
- PASS: Disposable Identity, Notification and Mailpit reached readiness
- PASS: Exactly 72 UTF-8 bytes is accepted and only a non-plaintext hash is stored
- PASS: 73-byte Register password is rejected before database or email side effects
- PASS: Register A returned 201 and remains pending despite forged fields
- PASS: Register A database state is pending/unverified and role is CUSTOMER
- PASS: Register response has no password/hash or verification-code fields
- PASS: Mailpit received one verification message for A
- PASS: Register B returned pending account
- PASS: Mailpit received one verification message for B
- PASS: A verification succeeded with unknown fields ignored
- PASS: Forged verification fields affected only A and did not change either CUSTOMER role
- PASS: A verification code cannot verify B
- PASS: Verified A can login before restart
- PASS: Login response does not expose password_hash
- PASS: JWKS is available before restart
- PASS: Forgot A returned generic accepted response
- PASS: A has one reset email in Mailpit
- PASS: Disposable schema has successful Flyway V1-V3 only
- PASS: Identity container restarted and returned healthy
- PASS: RSA public JWK stayed stable across Identity restart
- PASS: Pre-restart JWT is accepted after restart (protected missing route returns 404, anonymous returns 401)
- PASS: Pre-restart refresh session survived Identity restart and rotated
- PASS: B verification challenge remained usable after Identity restart
- PASS: B became active only after its own OTP
- PASS: B login created a separate session before A reset
- PASS: A reset challenge remained usable after Identity restart
- PASS: Notification container restarted and returned healthy
- PASS: Notification restart did not enqueue an automatic duplicate email
- PASS: Missing and invalid internal tokens were denied without sending email
- PASS: Authorized internal notification returned 202
- PASS: Unknown recipient field did not redirect the real Mailpit message
- PASS: Identity signing key is mounted read-only
- PASS: Disposable Postgres uses a project-scoped named volume
- PASS: Postgres returned healthy on the same container and named volume
- PASS: Postgres recovery did not apply or repair migrations
- PASS: Reset grant and transaction completed after PostgreSQL recovery
- PASS: Reset revoked all A refresh sessions that existed at commit time
- PASS: Old password is rejected and new password can login
- PASS: Reset token is one-use
- PASS: Successful reset revoked A refresh session
- PASS: B refresh session remains usable after A reset
- PASS: Existing access JWT remains accepted through restart and password reset
- PASS: Final database confirms A active with a non-plaintext password and B unchanged/active
- PASS: Final database confirms reset challenge consumed and B refresh session remains active
- PASS: PostgreSQL restart-to-healthy time: 5.7 seconds (observed; no SLA inferred)
- PASS: Disposable Flyway history before/after: 1:true,2:true,3:true
- PASS: Disposable data volume: stylemind-identity-audit-20261008-586ce25b_postgres-data
- PASS: OTP, access/refresh tokens, reset token and password values are omitted from evidence

## Contract gaps with observed behavior

- Identity forgot-password with a syntactically structured 255-character email returned HTTP 202; account count delta=0, Mailpit message delta=0
- Identity register with the same 255-character email returned HTTP 201; matching account rows=1, Mailpit messages=1; DTO/schema define no explicit email maximum
