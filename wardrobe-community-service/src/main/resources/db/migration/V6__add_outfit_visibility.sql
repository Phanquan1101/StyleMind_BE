-- Owner-approved EP06 Task 04 scope, 2026-10-29. Coordinate deployment with the new readers.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.outfit
        WHERE public_token IS NOT NULL
          AND (btrim(public_token) = '' OR char_length(public_token) > 128)
    ) THEN
        RAISE EXCEPTION 'Existing public link state requires review before Outfit visibility migration';
    END IF;
END;
$$;

ALTER TABLE public.outfit
    ADD COLUMN visibility text NOT NULL DEFAULT 'PRIVATE';

UPDATE public.outfit
SET visibility = CASE WHEN public_token IS NOT NULL THEN 'PUBLIC' ELSE 'PRIVATE' END,
    public_enabled_at = CASE
        WHEN public_token IS NOT NULL THEN COALESCE(public_enabled_at, updated_at)
        ELSE NULL
    END;

ALTER TABLE public.outfit
    ADD CONSTRAINT outfit_visibility_check
        CHECK (visibility IN ('PRIVATE', 'FRIENDS', 'PUBLIC')),
    ADD CONSTRAINT outfit_public_visibility_state_check
        CHECK (
            (visibility = 'PUBLIC'
                AND public_token IS NOT NULL
                AND btrim(public_token) <> ''
                AND char_length(public_token) <= 128
                AND public_enabled_at IS NOT NULL)
            OR
            (visibility IN ('PRIVATE', 'FRIENDS') AND public_enabled_at IS NULL)
        );

CREATE INDEX outfit_friend_visibility_page_idx
    ON public.outfit (owner_account_id, updated_at DESC, outfit_id DESC)
    WHERE visibility IN ('FRIENDS', 'PUBLIC');
