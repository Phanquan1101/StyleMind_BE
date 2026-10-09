# Identity — First Admin Provisioning (Operator-Controlled)

This is a manual, one-time operational procedure. There is intentionally no public endpoint, application startup seed, runtime backdoor, hard-coded account, or committed credential. This task does not provision an Admin in any persistent database.

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
