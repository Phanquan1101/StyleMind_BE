-- Physical mapping of 01_identity.mmd: lowercase singular snake_case names.
-- string -> text; datetime -> timestamp with time zone; decimal -> numeric.
-- No lengths, numeric bounds, generated values, account states or seeds are inferred.

CREATE TABLE public.account (
    account_id uuid PRIMARY KEY,
    email text NOT NULL UNIQUE,
    password_hash text NOT NULL,
    account_status text NOT NULL,
    email_verified_at timestamp with time zone,
    registered_at timestamp with time zone NOT NULL
);

CREATE TABLE public.account_role (
    account_id uuid NOT NULL REFERENCES public.account (account_id),
    role_code text NOT NULL,
    PRIMARY KEY (account_id, role_code),
    CONSTRAINT account_role_role_code_check CHECK (role_code IN ('CUSTOMER', 'ADMIN'))
);

CREATE TABLE public.user_profile (
    account_id uuid PRIMARY KEY REFERENCES public.account (account_id),
    display_name text NOT NULL
);

CREATE TABLE public.customer_address (
    address_id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES public.account (account_id),
    label text,
    recipient_name text NOT NULL,
    recipient_phone text NOT NULL,
    address_line text NOT NULL,
    ward_code text,
    is_default boolean NOT NULL
);

CREATE TABLE public.style_profile (
    account_id uuid PRIMARY KEY REFERENCES public.account (account_id),
    fit_preference text,
    height_cm numeric,
    usual_top_size text,
    usual_bottom_size text,
    shoe_size text
);

CREATE TABLE public.profile_style (
    account_id uuid NOT NULL REFERENCES public.style_profile (account_id),
    style_code text NOT NULL,
    PRIMARY KEY (account_id, style_code)
);

CREATE TABLE public.profile_color (
    account_id uuid NOT NULL REFERENCES public.style_profile (account_id),
    color_code text NOT NULL,
    PRIMARY KEY (account_id, color_code)
);

CREATE TABLE public.profile_brand (
    account_id uuid NOT NULL REFERENCES public.style_profile (account_id),
    -- Logical reference to Commerce only; no cross-database foreign key.
    brand_id uuid NOT NULL,
    PRIMARY KEY (account_id, brand_id)
);

CREATE TABLE public.refresh_session (
    session_id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES public.account (account_id),
    token_hash text NOT NULL UNIQUE,
    issued_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone
);
