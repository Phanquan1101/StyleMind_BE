-- Commerce product detail slice for US-03.02 through US-03.04.

ALTER TABLE public.product
    ADD CONSTRAINT product_status_check CHECK (product_status IN ('ACTIVE', 'INACTIVE'));

CREATE TABLE public.product_variant (
    variant_id uuid PRIMARY KEY,
    product_id uuid NOT NULL REFERENCES public.product (product_id),
    sku text NOT NULL UNIQUE,
    size text,
    color text,
    material text,
    unit_price_vnd bigint NOT NULL,
    variant_status text NOT NULL,
    CONSTRAINT product_variant_price_check CHECK (unit_price_vnd >= 0),
    CONSTRAINT product_variant_status_check CHECK (variant_status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE public.variant_image (
    image_id uuid PRIMARY KEY,
    variant_id uuid NOT NULL REFERENCES public.product_variant (variant_id) ON DELETE CASCADE,
    image_url text NOT NULL,
    alt_text text,
    display_order int NOT NULL,
    is_primary boolean NOT NULL,
    CONSTRAINT variant_image_display_order_check CHECK (display_order >= 0)
);

CREATE UNIQUE INDEX variant_image_variant_display_order_uq
    ON public.variant_image (variant_id, display_order);

CREATE UNIQUE INDEX variant_image_one_primary_per_variant_uq
    ON public.variant_image (variant_id)
    WHERE is_primary;
