\set ON_ERROR_STOP on

-- Dev/operator-only seed for a test Admin account.
-- Required psql variables:
--   account_id: UUID
--   email: normalized email
--   password_hash: precomputed password hash accepted by Identity's PasswordEncoder
--
-- Example shape:
-- psql "$STYLEMIND_IDENTITY_DB_URL" \
--   -v account_id='00000000-0000-0000-0000-000000000001' \
--   -v email='admin@example.test' \
--   -v password_hash='<bcrypt-hash>' \
--   -f identity-service/scripts/seed-dev-admin.sql

INSERT INTO public.account (
    account_id,
    email,
    password_hash,
    account_status,
    email_verified_at,
    registered_at
) VALUES (
    :'account_id'::uuid,
    :'email',
    :'password_hash',
    'ACTIVE',
    now(),
    now()
)
ON CONFLICT (email) DO UPDATE
SET password_hash = EXCLUDED.password_hash,
    account_status = 'ACTIVE',
    email_verified_at = COALESCE(public.account.email_verified_at, now());

INSERT INTO public.account_role (account_id, role_code)
VALUES (:'account_id'::uuid, 'ADMIN')
ON CONFLICT DO NOTHING;
