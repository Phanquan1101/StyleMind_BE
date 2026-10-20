-- Commerce cancellation request record for US-03.10.
-- Cancellation after payment and refund behavior remains outside this migration.

CREATE TABLE public.cancellation_request (
    cancellation_request_id uuid PRIMARY KEY,
    order_id uuid NOT NULL UNIQUE REFERENCES public.customer_order (order_id) ON DELETE CASCADE,
    requested_by_user_id uuid NOT NULL,
    reason text NOT NULL,
    request_status text NOT NULL,
    requested_at timestamptz NOT NULL,
    reviewed_by_user_id uuid,
    reviewed_at timestamptz,
    decision_reason text,
    CONSTRAINT cancellation_request_status_check CHECK (request_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT cancellation_request_review_check CHECK (
        (request_status = 'PENDING' AND reviewed_by_user_id IS NULL AND reviewed_at IS NULL)
        OR (request_status IN ('APPROVED', 'REJECTED') AND reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL)
    ),
    CONSTRAINT cancellation_request_rejection_reason_check CHECK (
        request_status <> 'REJECTED' OR decision_reason IS NOT NULL
    )
);
