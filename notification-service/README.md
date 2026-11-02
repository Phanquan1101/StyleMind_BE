# Notification — Identity email delivery

Notification implements the internal email-delivery endpoints used by Identity US-02.01 and US-02.04. It accepts an OTP only long enough to send it through SMTP. Notification does not create challenges, store OTP state, or connect to the Identity database.

## Internal endpoint

`POST /internal/v1/email-verifications`

Required header: `X-StyleMind-Service-Token`

Request fields:

```json
{
  "recipientEmail": "customer@example.com",
  "verificationCode": "012345"
}
```

The endpoint validates the email and six-digit string, then sends a fixed text message. It does not accept a template, URL, subject, or arbitrary body from the caller. The code is not placed in a URL/query parameter.

Responses: `202` after the SMTP server accepts the message; `400 INVALID_REQUEST`; `401 INTERNAL_SERVICE_UNAUTHORIZED` for an absent or invalid token; `503 EMAIL_DELIVERY_FAILED` for a definite failure; `504 EMAIL_DELIVERY_UNKNOWN` when an SMTP timeout makes acceptance uncertain. The service does not retry an uncertain send.

`POST /internal/v1/password-resets` uses the same service-token protection and SMTP transport with request fields `recipientEmail` and `resetCode`. It sends a fixed password-reset subject and template; callers cannot provide arbitrary content. The OTP is not placed in a URL.

## Configuration

`STYLEMIND_SERVICE_TOKEN` is required and must contain at least 32 bytes. It is compared in constant time. Missing/short token configuration fails startup. It is shared with Identity as a local service-to-service mechanism; production authentication/topology remains TBD.

SMTP is configured with `STYLEMIND_SMTP_HOST`, `STYLEMIND_SMTP_PORT`, `STYLEMIND_SMTP_USERNAME`, `STYLEMIND_SMTP_PASSWORD`, `STYLEMIND_SMTP_AUTH`, `STYLEMIND_SMTP_STARTTLS`, and `STYLEMIND_EMAIL_FROM`. Connection, read, and write timeouts are controlled by `STYLEMIND_SMTP_CONNECT_TIMEOUT_MS`, `STYLEMIND_SMTP_READ_TIMEOUT_MS`, and `STYLEMIND_SMTP_WRITE_TIMEOUT_MS` and have finite defaults. Do not log SMTP credentials, service tokens, or email contents.

For local Compose the default SMTP host is Mailpit, which captures messages and does not deliver to customer mailboxes. A user-managed ignored `.env` may override those defaults; this documentation pass did not inspect that file or its secrets. The owner previously selected Mailjet SMTP for local configuration, but the external provider was not exercised by the current audit, and no production email provider or topology is approved here. Keep credentials in ignored `.env`; never commit or log them.

## Verification

From the repository root:

```powershell
mvn -f notification-service/pom.xml verify
```

Identity end-to-end suites start this actual service artifact in a container and route SMTP into a real Mailpit test container. They verify registration and password-reset delivery without contacting an external mailbox. Notification returns `202` only after SMTP accepts the message; other successful HTTP codes do not confirm delivery, and uncertain sends are not retried. See the [current project context](../docs/governance/CURRENT_PROJECT_CONTEXT.md) and [Identity audit follow-up](../docs/qa/identity-audit/20261008T1144-0700/IDENTITY_AUDIT_FOLLOW_UP.md) for evidence boundaries.
