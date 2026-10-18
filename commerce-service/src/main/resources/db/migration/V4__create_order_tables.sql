-- Commerce checkout and order slice for US-03.08.

CREATE TABLE public.customer_order (
    order_id uuid PRIMARY KEY,
    order_number text NOT NULL UNIQUE,
    customer_id uuid NOT NULL,
    checkout_key text NOT NULL,
    order_status text NOT NULL,
    payment_method text NOT NULL,
    accepted_total_vnd bigint NOT NULL,
    placed_at timestamptz NOT NULL,
    completed_at timestamptz,
    shipping_carrier text,
    tracking_reference text,
    CONSTRAINT customer_order_status_check CHECK (
        order_status IN ('PENDING_PAYMENT', 'PLACED', 'PAID', 'PROCESSING', 'SHIPPED', 'COMPLETED', 'CANCELLED')
    ),
    CONSTRAINT customer_order_payment_method_check CHECK (payment_method IN ('COD', 'SEPAY_VIETQR')),
    CONSTRAINT customer_order_total_check CHECK (accepted_total_vnd >= 0)
);

CREATE UNIQUE INDEX customer_order_customer_checkout_key_uq
    ON public.customer_order (customer_id, checkout_key);

CREATE TABLE public.order_address_snapshot (
    order_id uuid PRIMARY KEY REFERENCES public.customer_order (order_id) ON DELETE CASCADE,
    source_address_id uuid,
    recipient_name text NOT NULL,
    recipient_phone text NOT NULL,
    address_line text NOT NULL
);

CREATE TABLE public.order_item (
    order_item_id uuid PRIMARY KEY,
    order_id uuid NOT NULL REFERENCES public.customer_order (order_id) ON DELETE CASCADE,
    variant_id uuid NOT NULL REFERENCES public.product_variant (variant_id),
    sku_snapshot text NOT NULL,
    product_name_snapshot text NOT NULL,
    brand_name_snapshot text NOT NULL,
    color_snapshot text,
    size_snapshot text,
    material_snapshot text,
    image_url_snapshot text,
    quantity int NOT NULL,
    unit_price_vnd bigint NOT NULL,
    CONSTRAINT order_item_quantity_check CHECK (quantity > 0),
    CONSTRAINT order_item_price_check CHECK (unit_price_vnd >= 0)
);
