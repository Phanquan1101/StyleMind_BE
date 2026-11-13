ALTER TABLE public.product
    DROP CONSTRAINT IF EXISTS product_status_check;

ALTER TABLE public.product
    ADD CONSTRAINT product_status_check
    CHECK (product_status IN ('ACTIVE', 'INACTIVE', 'DELETED'));
