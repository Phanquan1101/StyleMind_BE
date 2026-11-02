-- Wardrobe Community outfit sharing slice for US-05.03.
-- An owner can share one outfit with an email address (outfit_share) and can enable one public link.
-- recipient_email is stored trimmed and lowercase by the application; there is no foreign key to Identity.

ALTER TABLE public.outfit
    ADD COLUMN public_token text,
    ADD COLUMN public_enabled_at timestamptz,
    ADD CONSTRAINT outfit_public_token_uq UNIQUE (public_token);

CREATE TABLE public.outfit_share (
    outfit_share_id uuid PRIMARY KEY,
    outfit_id uuid NOT NULL REFERENCES public.outfit (outfit_id) ON DELETE CASCADE,
    recipient_email text NOT NULL,
    granted_at timestamptz NOT NULL,
    revoked_at timestamptz,
    CONSTRAINT outfit_share_outfit_recipient_uq UNIQUE (outfit_id, recipient_email)
);

CREATE INDEX outfit_share_recipient_idx
    ON public.outfit_share (recipient_email);
