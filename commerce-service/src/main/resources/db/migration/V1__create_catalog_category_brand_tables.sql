-- Physical Commerce catalog slice for US-03.01.
-- Category and Brand are authoritative Commerce reference data.

CREATE TABLE public.category (
    category_id uuid PRIMARY KEY,
    name text NOT NULL,
    slug text NOT NULL UNIQUE,
    is_active boolean NOT NULL
);

CREATE TABLE public.brand (
    brand_id uuid PRIMARY KEY,
    name text NOT NULL,
    slug text NOT NULL UNIQUE,
    is_active boolean NOT NULL
);

CREATE TABLE public.product (
    product_id uuid PRIMARY KEY,
    category_id uuid NOT NULL REFERENCES public.category (category_id),
    brand_id uuid NOT NULL REFERENCES public.brand (brand_id),
    name text NOT NULL,
    description text,
    demographic_segment text,
    product_status text NOT NULL
);
