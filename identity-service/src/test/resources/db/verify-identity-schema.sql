\set ON_ERROR_STOP on

-- Real PostgreSQL catalog assertions. No fixture accounts, writes or OTP substitutes.
BEGIN READ ONLY;
SET LOCAL search_path = public, pg_catalog;

DO $verify$
DECLARE
    differences text;
BEGIN
    IF current_database() <> 'stylemind_identity' THEN
        RAISE EXCEPTION 'Verification requires the stylemind_identity database';
    END IF;
    IF current_setting('server_version_num')::integer / 10000 <> 18 THEN
        RAISE EXCEPTION 'Verification requires PostgreSQL 18';
    END IF;

    WITH expected(table_name) AS (
        VALUES ('account'), ('account_role'), ('user_profile'), ('customer_address'),
               ('style_profile'), ('profile_style'), ('profile_color'), ('profile_brand'),
               ('refresh_session'), ('flyway_schema_history')
    ), actual AS (
        SELECT table_name::text FROM information_schema.tables WHERE table_schema = 'public'
    ), delta AS (
        (SELECT * FROM expected EXCEPT SELECT * FROM actual)
        UNION ALL
        (SELECT * FROM actual EXCEPT SELECT * FROM expected)
    )
    SELECT string_agg(table_name, ', ') INTO differences FROM delta;
    IF differences IS NOT NULL THEN
        RAISE EXCEPTION 'Unexpected or missing tables: %', differences;
    END IF;

    -- Exactly the attributes/types/nullability in the V1 schema contract.
    WITH expected(table_name, column_name, udt_name, is_nullable) AS (
        VALUES
        ('account', 'account_id', 'uuid', 'NO'),
        ('account', 'email', 'text', 'NO'),
        ('account', 'password_hash', 'text', 'NO'),
        ('account', 'account_status', 'text', 'NO'),
        ('account', 'email_verified_at', 'timestamptz', 'YES'),
        ('account', 'registered_at', 'timestamptz', 'NO'),
        ('account_role', 'account_id', 'uuid', 'NO'),
        ('account_role', 'role_code', 'text', 'NO'),
        ('user_profile', 'account_id', 'uuid', 'NO'),
        ('user_profile', 'display_name', 'text', 'NO'),
        ('customer_address', 'address_id', 'uuid', 'NO'),
        ('customer_address', 'account_id', 'uuid', 'NO'),
        ('customer_address', 'label', 'text', 'YES'),
        ('customer_address', 'recipient_name', 'text', 'NO'),
        ('customer_address', 'recipient_phone', 'text', 'NO'),
        ('customer_address', 'address_line', 'text', 'NO'),
        ('customer_address', 'ward_code', 'text', 'YES'),
        ('customer_address', 'is_default', 'bool', 'NO'),
        ('style_profile', 'account_id', 'uuid', 'NO'),
        ('style_profile', 'fit_preference', 'text', 'YES'),
        ('style_profile', 'height_cm', 'numeric', 'YES'),
        ('style_profile', 'usual_top_size', 'text', 'YES'),
        ('style_profile', 'usual_bottom_size', 'text', 'YES'),
        ('style_profile', 'shoe_size', 'text', 'YES'),
        ('profile_style', 'account_id', 'uuid', 'NO'),
        ('profile_style', 'style_code', 'text', 'NO'),
        ('profile_color', 'account_id', 'uuid', 'NO'),
        ('profile_color', 'color_code', 'text', 'NO'),
        ('profile_brand', 'account_id', 'uuid', 'NO'),
        ('profile_brand', 'brand_id', 'uuid', 'NO'),
        ('refresh_session', 'session_id', 'uuid', 'NO'),
        ('refresh_session', 'account_id', 'uuid', 'NO'),
        ('refresh_session', 'token_hash', 'text', 'NO'),
        ('refresh_session', 'issued_at', 'timestamptz', 'NO'),
        ('refresh_session', 'expires_at', 'timestamptz', 'NO'),
        ('refresh_session', 'revoked_at', 'timestamptz', 'YES')
    ), actual AS (
        SELECT table_name::text, column_name::text, udt_name::text, is_nullable::text
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'
    ), delta AS (
        (SELECT * FROM expected EXCEPT SELECT * FROM actual)
        UNION ALL
        (SELECT * FROM actual EXCEPT SELECT * FROM expected)
    )
    SELECT string_agg(row_to_json(delta)::text, E'\n') INTO differences FROM delta;
    IF differences IS NOT NULL THEN
        RAISE EXCEPTION 'Column/type/nullability mismatch: %', differences;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'
          AND (column_default IS NOT NULL OR is_identity <> 'NO' OR is_generated <> 'NEVER'
               OR character_maximum_length IS NOT NULL
               OR (udt_name = 'numeric' AND numeric_precision IS NOT NULL))
    ) THEN
        RAISE EXCEPTION 'Unapproved default, generated value, length or numeric bound';
    END IF;

    WITH expected(table_name, kind, columns, target_schema, target_table, target_columns) AS (
        VALUES
        ('account', 'p', 'account_id', '', '', ''),
        ('account', 'u', 'email', '', '', ''),
        ('account_role', 'p', 'account_id,role_code', '', '', ''),
        ('account_role', 'f', 'account_id', 'public', 'account', 'account_id'),
        ('user_profile', 'p', 'account_id', '', '', ''),
        ('user_profile', 'f', 'account_id', 'public', 'account', 'account_id'),
        ('customer_address', 'p', 'address_id', '', '', ''),
        ('customer_address', 'f', 'account_id', 'public', 'account', 'account_id'),
        ('style_profile', 'p', 'account_id', '', '', ''),
        ('style_profile', 'f', 'account_id', 'public', 'account', 'account_id'),
        ('profile_style', 'p', 'account_id,style_code', '', '', ''),
        ('profile_style', 'f', 'account_id', 'public', 'style_profile', 'account_id'),
        ('profile_color', 'p', 'account_id,color_code', '', '', ''),
        ('profile_color', 'f', 'account_id', 'public', 'style_profile', 'account_id'),
        ('profile_brand', 'p', 'account_id,brand_id', '', '', ''),
        ('profile_brand', 'f', 'account_id', 'public', 'style_profile', 'account_id'),
        ('refresh_session', 'p', 'session_id', '', '', ''),
        ('refresh_session', 'u', 'token_hash', '', '', ''),
        ('refresh_session', 'f', 'account_id', 'public', 'account', 'account_id')
    ), actual AS (
        SELECT c.relname::text, k.contype::text,
               (SELECT string_agg(a.attname, ',' ORDER BY x.position)
                FROM unnest(k.conkey) WITH ORDINALITY AS x(attnum, position)
                JOIN pg_attribute a ON a.attrelid = k.conrelid AND a.attnum = x.attnum),
               coalesce(rn.nspname::text, ''), coalesce(r.relname::text, ''),
               coalesce((SELECT string_agg(a.attname, ',' ORDER BY x.position)
                         FROM unnest(k.confkey) WITH ORDINALITY AS x(attnum, position)
                         JOIN pg_attribute a ON a.attrelid = k.confrelid AND a.attnum = x.attnum), '')
        FROM pg_constraint k
        JOIN pg_class c ON c.oid = k.conrelid
        JOIN pg_namespace n ON n.oid = c.relnamespace
        LEFT JOIN pg_class r ON r.oid = k.confrelid
        LEFT JOIN pg_namespace rn ON rn.oid = r.relnamespace
        WHERE n.nspname = 'public' AND c.relname <> 'flyway_schema_history'
          AND k.contype IN ('p', 'u', 'f')
    ), delta AS (
        (SELECT * FROM expected EXCEPT SELECT * FROM actual)
        UNION ALL
        (SELECT * FROM actual EXCEPT SELECT * FROM expected)
    )
    SELECT string_agg(row_to_json(delta)::text, E'\n') INTO differences FROM delta;
    IF differences IS NOT NULL THEN
        RAISE EXCEPTION 'PK/unique/FK mismatch: %', differences;
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_constraint k
        JOIN pg_class c ON c.oid = k.conrelid
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relname <> 'flyway_schema_history'
          AND (NOT k.convalidated OR k.condeferrable
               OR (k.contype = 'f' AND (k.confdeltype <> 'a' OR k.confupdtype <> 'a')))
    ) THEN
        RAISE EXCEPTION 'Unexpected deferred, unvalidated or cascading constraint';
    END IF;

    -- Only the role vocabulary explicitly present in the model is constrained.
    SELECT string_agg(c.relname || ':' || pg_get_constraintdef(k.oid), E'\n')
    INTO differences
    FROM pg_constraint k
    JOIN pg_class c ON c.oid = k.conrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relname <> 'flyway_schema_history' AND k.contype = 'c';
    IF differences IS DISTINCT FROM
       'account_role:CHECK ((role_code = ANY (ARRAY[''CUSTOMER''::text, ''ADMIN''::text])))' THEN
        RAISE EXCEPTION 'Role vocabulary check mismatch or extra business checks: %', differences;
    END IF;

    IF (SELECT count(*) FROM public.flyway_schema_history) <> 1
       OR NOT EXISTS (SELECT 1 FROM public.flyway_schema_history
                      WHERE version = '1' AND type = 'SQL' AND success
                        AND script = 'V1__create_identity_tables.sql' AND checksum IS NOT NULL) THEN
        RAISE EXCEPTION 'Expected exactly one successful V1 Flyway migration';
    END IF;
END
$verify$;

SELECT 'PASS: PostgreSQL 18; 9 model tables; 36 columns; 9 PKs; 2 unique keys; 8 local FKs; role vocabulary; V1 history' AS result;
COMMIT;
