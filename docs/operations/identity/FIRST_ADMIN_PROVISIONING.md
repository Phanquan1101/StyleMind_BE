# Identity — First Admin Provisioning

## Local Docker Compose bootstrap

The owner approved a local-only bootstrap for team development. It is available only when Identity runs with the Compose-provided `compose-local` Spring profile and `STYLEMIND_BOOTSTRAP_ADMIN_ENABLED=true`.

Set `STYLEMIND_BOOTSTRAP_ADMIN_EMAIL` and `STYLEMIND_BOOTSTRAP_ADMIN_PASSWORD` in the ignored local `.env`; never put real values in Git. On first startup, if that normalized email is absent from `stylemind_identity`, the bootstrap creates one account with status `ACTIVE`, `email_verified_at` set, a password hash produced by Identity's configured `PasswordEncoder`, and the `ADMIN` role. This trusted local bootstrap is the explicit exception to Register OTP; it creates no OTP row and sends no email. No migration or public API is involved.

The operation is insert-only. Repeated startups leave an already-provisioned Admin unchanged. If the email already belongs to another account, the bootstrap does not activate, verify, promote, or change its password/roles; it logs a generic warning and leaves that row untouched. Updating the environment password does not rotate an existing account. The flag is disabled by default. After creating the local Admin, it may be disabled while the account remains in the persistent Compose volume. This local exception does not define or authorize production behavior.

## Other environments: operator-controlled provisioning

For runtimes outside the explicitly local Compose profile, there is no startup seed or public endpoint. Continue to use the operator-controlled procedure below. Production topology is not inferred here.

## Preconditions

1. Obtain an approved operator/change ticket identifying the exact environment and Identity database.
2. Schedule a maintenance window and stop or quiesce Identity writers. Direct SQL does not participate in the Manage Users advisory lock.
3. Use a trusted PostgreSQL operator credential through the approved secret manager/terminal. Do not put passwords in shell history, this repository, a script, or this document.
4. The target account must already exist, be ACTIVE, and have non-null email_verified_at. The person must have completed the normal verified-account flow. Never create an account or bypass email verification here.
5. Record the exact database name/host out-of-band. Production topology is not inferred here.

## Transaction procedure

Connect using the approved operator mechanism, verify the selected database first, and stop if it is not the expected Identity database. Use psql with ON_ERROR_STOP enabled. Replace the UUID placeholder only in the private operator session:

    SELECT current_database(), current_user;

    BEGIN;
    SELECT account_id, account_status, email_verified_at
      FROM public.account
     WHERE account_id = :'target_account_id'::uuid
     FOR UPDATE;

If that returns anything other than exactly one expected account that is ACTIVE and verified, issue ROLLBACK and investigate. Do not substitute another account by email or guess an ID.

Provision the ADMIN role while preserving the existing CUSTOMER role:

    INSERT INTO public.account_role (account_id, role_code)
    VALUES (:'target_account_id'::uuid, 'ADMIN')
    ON CONFLICT (account_id, role_code) DO NOTHING;
    COMMIT;

If any statement fails, ROLLBACK. Do not update account_status or email_verified_at, delete roles, edit credentials, or touch sessions. The target may have both CUSTOMER and ADMIN, as approved.

## Verify and record

Reconnect/query the approved Identity database and verify:

    SELECT a.account_id, a.account_status, a.email_verified_at, r.role_code
      FROM public.account a
      JOIN public.account_role r USING (account_id)
     WHERE a.account_id = :'target_account_id'::uuid
     ORDER BY r.role_code;

Confirm the same account ID is ACTIVE and verified and that role ADMIN is present. Record the ticket, operator, timestamp, environment/database identifier, and before/after role set in the organization’s change record. Do not store credentials or access tokens in that record.

This operation is outside application-level last-Admin locking. Keep Identity writers quiesced for the transaction and verify there remains at least one qualifying Admin before ending maintenance. Do not run this procedure against the disposable test stack unless a local runtime test explicitly requires an operator fixture; test fixtures should otherwise be created and cleaned only by the isolated test harness.
