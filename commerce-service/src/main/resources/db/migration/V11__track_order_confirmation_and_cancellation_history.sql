ALTER TABLE public.customer_order
    ADD COLUMN confirmation_state text NOT NULL DEFAULT 'LEGACY_UNKNOWN',
    ADD COLUMN confirmed_by_user_id uuid,
    ADD COLUMN confirmed_at timestamptz;

ALTER TABLE public.customer_order
    ADD CONSTRAINT customer_order_confirmation_state_check
        CHECK (confirmation_state IN ('LEGACY_UNKNOWN', 'UNCONFIRMED', 'CONFIRMED')),
    ADD CONSTRAINT customer_order_confirmation_details_check CHECK (
        (confirmation_state = 'CONFIRMED' AND confirmed_by_user_id IS NOT NULL AND confirmed_at IS NOT NULL)
        OR (confirmation_state IN ('LEGACY_UNKNOWN', 'UNCONFIRMED') AND confirmed_by_user_id IS NULL AND confirmed_at IS NULL)
    );

ALTER TABLE public.cancellation_request DROP CONSTRAINT cancellation_request_order_id_key;
CREATE UNIQUE INDEX cancellation_request_one_pending_per_order_uq
    ON public.cancellation_request (order_id) WHERE request_status = 'PENDING';
CREATE INDEX cancellation_request_order_history_idx
    ON public.cancellation_request (order_id, requested_at DESC, cancellation_request_id DESC);
