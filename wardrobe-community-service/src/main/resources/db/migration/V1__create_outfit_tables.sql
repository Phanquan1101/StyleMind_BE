-- Wardrobe Community outfit slice for US-05.01.
-- "My Wardrobe" is the set of a customer's outfits; there is no wardrobe table.
-- owner_account_id and variant_id are logical references to Identity and Commerce (no cross-service FK).
-- Product name, price and image are not stored; they are read from Commerce when an outfit is displayed.

CREATE TABLE public.outfit (
    outfit_id uuid PRIMARY KEY,
    owner_account_id uuid NOT NULL,
    name text NOT NULL,
    creation_source text NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT outfit_name_length_check CHECK (char_length(name) BETWEEN 1 AND 100),
    CONSTRAINT outfit_creation_source_check CHECK (creation_source IN ('MANUAL', 'AI'))
);

CREATE INDEX outfit_owner_updated_idx
    ON public.outfit (owner_account_id, updated_at DESC);

CREATE TABLE public.outfit_item (
    outfit_item_id uuid PRIMARY KEY,
    outfit_id uuid NOT NULL REFERENCES public.outfit (outfit_id) ON DELETE CASCADE,
    variant_id uuid NOT NULL,
    display_order int NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT outfit_item_display_order_check CHECK (display_order >= 0),
    CONSTRAINT outfit_item_outfit_variant_uq UNIQUE (outfit_id, variant_id)
);
