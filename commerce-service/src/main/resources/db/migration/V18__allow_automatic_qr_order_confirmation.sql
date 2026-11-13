ALTER TABLE public.customer_order
    DROP CONSTRAINT customer_order_confirmation_state_check,
    DROP CONSTRAINT customer_order_confirmation_details_check;

ALTER TABLE public.customer_order
    ADD CONSTRAINT customer_order_confirmation_state_check
        CHECK (confirmation_state IN ('LEGACY_UNKNOWN', 'UNCONFIRMED', 'CONFIRMED', 'AUTO_CONFIRMED')),
    ADD CONSTRAINT customer_order_confirmation_details_check CHECK (
        (confirmation_state = 'CONFIRMED' AND confirmed_by_user_id IS NOT NULL AND confirmed_at IS NOT NULL)
        OR (confirmation_state = 'AUTO_CONFIRMED' AND confirmed_by_user_id IS NULL AND confirmed_at IS NOT NULL)
        OR (confirmation_state IN ('LEGACY_UNKNOWN', 'UNCONFIRMED') AND confirmed_by_user_id IS NULL AND confirmed_at IS NULL)
    );
