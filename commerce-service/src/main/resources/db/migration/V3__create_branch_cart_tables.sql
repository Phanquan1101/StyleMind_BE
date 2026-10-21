-- Commerce branch and cart slice for US-03.05 through US-03.07.

CREATE TABLE public.branch (
    branch_id uuid PRIMARY KEY,
    branch_code text NOT NULL UNIQUE,
    name text NOT NULL,
    address_line text NOT NULL,
    contact_phone text,
    branch_status text NOT NULL,
    CONSTRAINT branch_status_check CHECK (branch_status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE public.cart (
    cart_id uuid PRIMARY KEY,
    customer_id uuid,
    guest_session_key text,
    cart_status text NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    merged_at timestamptz,
    CONSTRAINT cart_status_check CHECK (cart_status IN ('ACTIVE', 'MERGED', 'CHECKED_OUT')),
    CONSTRAINT cart_owner_check CHECK (
        (customer_id IS NOT NULL AND guest_session_key IS NULL)
        OR (customer_id IS NULL AND guest_session_key IS NOT NULL)
    )
);

CREATE UNIQUE INDEX cart_active_customer_uq
    ON public.cart (customer_id)
    WHERE customer_id IS NOT NULL AND cart_status = 'ACTIVE';

CREATE UNIQUE INDEX cart_active_guest_uq
    ON public.cart (guest_session_key)
    WHERE guest_session_key IS NOT NULL AND cart_status = 'ACTIVE';

CREATE TABLE public.cart_item (
    cart_item_id uuid PRIMARY KEY,
    cart_id uuid NOT NULL REFERENCES public.cart (cart_id) ON DELETE CASCADE,
    variant_id uuid NOT NULL REFERENCES public.product_variant (variant_id),
    quantity int NOT NULL,
    last_seen_price_vnd bigint NOT NULL,
    added_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT cart_item_quantity_check CHECK (quantity > 0),
    CONSTRAINT cart_item_price_check CHECK (last_seen_price_vnd >= 0)
);

CREATE UNIQUE INDEX cart_item_cart_variant_uq
    ON public.cart_item (cart_id, variant_id);
