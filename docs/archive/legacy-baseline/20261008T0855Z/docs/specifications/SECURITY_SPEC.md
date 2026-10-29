# StyleMind V1 Security Specification

Status: IMPLEMENTATION BASELINE

Authority: DEC-036 and approved BRD/PRD/SAD. This is a concise V1 baseline, not enterprise IAM.

## Identity and tokens

- Spring Security is the backend security foundation.
- Roles: `ROLE_USER` and `ROLE_ADMIN`.
- Access tokens use JWT. Refresh tokens are opaque, hashed at rest and revocable.
- Passwords use a modern adaptive encoder (`BCryptPasswordEncoder` in the initial implementation).
- OTP is used only where approved requirements require it; OTP values are short-lived and never logged.
- Token secrets and signing configuration come from environment variables; no credentials are committed.

## Authorization

- Public catalog browsing is anonymous.
- User resources require an authenticated user and an ownership check against the resource owner.
- Admin catalog CRUD, QC and operational actions require `ROLE_ADMIN`.
- Service-to-service calls use explicit internal credentials/configuration; direct database access across service boundaries is prohibited.

## Input and errors

- Validate request DTOs at the API boundary.
- Use safe, stable errors: `{ code, message, timestamp, details }`.
- Do not return stack traces, SQL errors, tokens, secrets or provider diagnostics to clients.
- Log security-relevant correlation identifiers without logging passwords, OTPs or payment credentials.

## Payment and sensitive data

- Payment webhooks require provider authentication/signature verification before processing.
- Exact-match payment confirmation and idempotency are mandatory for the approved payment flow.
- User images, body/context data and Wardrobe data require ownership checks and later retention/deletion controls.

## Local and deployment baseline

- Local secrets use environment variables or an uncommitted `.env` file.
- Production secret management, rotation, consent/retention details and full threat model remain follow-up work.
