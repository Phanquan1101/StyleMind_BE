ALTER TABLE public.brand
    ADD COLUMN deleted_at timestamptz;

ALTER TABLE public.brand
    DROP CONSTRAINT brand_slug_key;

CREATE UNIQUE INDEX ux_brand_slug_not_deleted
    ON public.brand (slug)
    WHERE deleted_at IS NULL;
