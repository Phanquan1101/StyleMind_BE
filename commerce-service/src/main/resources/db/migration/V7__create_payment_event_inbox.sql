-- US-04.02: remote IDs are logical references, never cross-service foreign keys.
CREATE TABLE public.payment_event_inbox (
    event_id uuid PRIMARY KEY,
    event_fingerprint char(64) NOT NULL,
    order_id uuid NOT NULL,
    payment_attempt_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    payment_method text NOT NULL CHECK (payment_method = 'SEPAY_VIETQR'),
    payment_reference text NOT NULL,
    amount_vnd bigint NOT NULL CHECK (amount_vnd > 0),
    confirmed_at timestamptz NOT NULL,
    received_at timestamptz NOT NULL,
    outcome text NOT NULL CHECK (outcome IN ('APPLIED', 'DUPLICATE', 'RECONCILIATION_REQUIRED')),
    reason text NOT NULL
);
CREATE UNIQUE INDEX payment_event_inbox_applied_order_uq
    ON public.payment_event_inbox(order_id) WHERE outcome = 'APPLIED';
CREATE INDEX payment_event_inbox_outcome_received_idx
    ON public.payment_event_inbox(outcome, received_at, event_id);
