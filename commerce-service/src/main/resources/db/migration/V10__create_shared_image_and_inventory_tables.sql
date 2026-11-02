-- Shared image assets and global variant inventory approved for catalog seeding.

CREATE TABLE public.image_asset (
    image_asset_id uuid PRIMARY KEY,
    image_url text NOT NULL,
    created_at timestamptz NOT NULL
);

INSERT INTO public.image_asset(image_asset_id, image_url, created_at)
SELECT image_id, image_url, CURRENT_TIMESTAMP
FROM public.variant_image;

ALTER TABLE public.variant_image
    ADD COLUMN image_asset_id uuid;

UPDATE public.variant_image
SET image_asset_id = image_id;

ALTER TABLE public.variant_image
    ALTER COLUMN image_asset_id SET NOT NULL,
    ADD CONSTRAINT variant_image_asset_fk
        FOREIGN KEY (image_asset_id) REFERENCES public.image_asset(image_asset_id),
    DROP COLUMN image_url;

CREATE INDEX variant_image_asset_idx
    ON public.variant_image(image_asset_id);

CREATE TABLE public.variant_inventory (
    variant_id uuid PRIMARY KEY REFERENCES public.product_variant(variant_id) ON DELETE CASCADE,
    on_hand_quantity int NOT NULL,
    reserved_quantity int NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT variant_inventory_on_hand_check CHECK (on_hand_quantity >= 0),
    CONSTRAINT variant_inventory_reserved_check CHECK (reserved_quantity >= 0),
    CONSTRAINT variant_inventory_available_check CHECK (reserved_quantity <= on_hand_quantity)
);

INSERT INTO public.variant_inventory(variant_id, on_hand_quantity, reserved_quantity, updated_at)
SELECT variant_id, 0, 0, CURRENT_TIMESTAMP
FROM public.product_variant;

CREATE TABLE public.inventory_reservation (
    reservation_id uuid PRIMARY KEY,
    order_id uuid NOT NULL REFERENCES public.customer_order(order_id) ON DELETE CASCADE,
    variant_id uuid NOT NULL REFERENCES public.product_variant(variant_id),
    quantity int NOT NULL,
    reservation_status text NOT NULL,
    expires_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT inventory_reservation_quantity_check CHECK (quantity > 0),
    CONSTRAINT inventory_reservation_status_check
        CHECK (reservation_status IN ('ACTIVE', 'CONSUMED', 'RELEASED')),
    CONSTRAINT inventory_reservation_expiry_check CHECK (
        (reservation_status = 'ACTIVE' AND expires_at IS NOT NULL)
        OR reservation_status IN ('CONSUMED', 'RELEASED')
    ),
    CONSTRAINT inventory_reservation_order_variant_uq UNIQUE(order_id, variant_id)
);

CREATE INDEX inventory_reservation_expiry_idx
    ON public.inventory_reservation(expires_at, order_id)
    WHERE reservation_status = 'ACTIVE';
