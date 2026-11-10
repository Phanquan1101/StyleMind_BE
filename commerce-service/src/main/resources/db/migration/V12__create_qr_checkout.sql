CREATE TABLE public.qr_checkout (
    checkout_id uuid PRIMARY KEY,
    customer_id uuid,
    guest_session_hash varchar(64),
    checkout_key text NOT NULL,
    checkout_status text NOT NULL,
    total_vnd bigint NOT NULL CHECK (total_vnd > 0),
    shipping_snapshot jsonb NOT NULL CHECK (jsonb_typeof(shipping_snapshot) = 'object'),
    payment_reference_id uuid NOT NULL UNIQUE,
    order_id uuid UNIQUE REFERENCES public.customer_order(order_id),
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CHECK ((customer_id IS NOT NULL AND guest_session_hash IS NULL) OR
           (customer_id IS NULL AND guest_session_hash IS NOT NULL)),
    CHECK (checkout_status IN ('PREPARING','AWAITING_PAYMENT','CANCELLED','EXPIRED','ORDER_CREATED','RECONCILIATION_REQUIRED')),
    CHECK (expires_at > created_at),
    CHECK ((checkout_status = 'ORDER_CREATED' AND order_id IS NOT NULL) OR
           (checkout_status <> 'ORDER_CREATED' AND order_id IS NULL))
);
CREATE UNIQUE INDEX qr_checkout_customer_key_uq ON public.qr_checkout(customer_id, checkout_key) WHERE customer_id IS NOT NULL;
CREATE UNIQUE INDEX qr_checkout_guest_key_uq ON public.qr_checkout(guest_session_hash, checkout_key) WHERE guest_session_hash IS NOT NULL;
CREATE INDEX qr_checkout_expiry_idx ON public.qr_checkout(expires_at) WHERE checkout_status IN ('PREPARING','AWAITING_PAYMENT');

CREATE TABLE public.qr_checkout_item (
    checkout_id uuid NOT NULL REFERENCES public.qr_checkout(checkout_id),
    variant_id uuid NOT NULL REFERENCES public.product_variant(variant_id),
    quantity integer NOT NULL CHECK (quantity > 0),
    unit_price_vnd bigint NOT NULL CHECK (unit_price_vnd > 0),
    product_snapshot jsonb NOT NULL CHECK (jsonb_typeof(product_snapshot) = 'object'),
    reservation_status text NOT NULL CHECK (reservation_status IN ('ACTIVE','CONSUMED','RELEASED')),
    PRIMARY KEY (checkout_id, variant_id)
);
