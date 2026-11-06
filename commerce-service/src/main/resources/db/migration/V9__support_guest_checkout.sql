-- Owner-approved guest checkout scope. Existing customer orders remain unchanged.
ALTER TABLE public.customer_order
    ALTER COLUMN customer_id DROP NOT NULL,
    ADD COLUMN guest_session_hash char(64);

ALTER TABLE public.customer_order
    ADD CONSTRAINT customer_order_owner_check CHECK (
        (customer_id IS NOT NULL AND guest_session_hash IS NULL)
        OR (customer_id IS NULL AND guest_session_hash IS NOT NULL)
    );

CREATE UNIQUE INDEX customer_order_guest_checkout_key_uq
    ON public.customer_order (guest_session_hash, checkout_key)
    WHERE guest_session_hash IS NOT NULL;

ALTER TABLE public.payment_event_inbox
    ALTER COLUMN customer_id DROP NOT NULL;
