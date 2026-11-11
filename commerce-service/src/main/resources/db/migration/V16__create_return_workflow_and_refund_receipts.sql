ALTER TABLE public.cancellation_refund_obligation
    DROP CONSTRAINT cancellation_refund_obligation_obligation_status_check,
    ADD CONSTRAINT cancellation_refund_obligation_obligation_status_check
        CHECK (obligation_status IN ('PENDING_REFUND', 'REFUNDED'));

CREATE TABLE public.return_request (
    return_id uuid PRIMARY KEY,
    order_id uuid NOT NULL REFERENCES public.customer_order(order_id),
    payment_event_id uuid NOT NULL REFERENCES public.payment_event_inbox(event_id),
    request_key uuid NOT NULL,
    request_fingerprint char(64) NOT NULL,
    return_status text NOT NULL CHECK (return_status IN
        ('REQUESTED', 'MORE_IMAGES_REQUESTED', 'PICKUP_APPROVED', 'REJECTED', 'PENDING_REFUND', 'REFUNDED')),
    reason text NOT NULL CHECK (char_length(reason) BETWEEN 1 AND 1000),
    decision_reason text CHECK (char_length(decision_reason) BETWEEN 1 AND 1000),
    amount_vnd bigint NOT NULL CHECK (amount_vnd > 0),
    created_at timestamptz NOT NULL,
    UNIQUE (order_id, request_key)
);
CREATE INDEX return_request_admin_created_idx
    ON public.return_request(created_at DESC, return_id DESC);

CREATE TABLE public.return_request_item (
    return_id uuid NOT NULL REFERENCES public.return_request(return_id) ON DELETE CASCADE,
    order_item_id uuid NOT NULL REFERENCES public.order_item(order_item_id),
    quantity integer NOT NULL CHECK (quantity > 0),
    unit_price_vnd bigint NOT NULL CHECK (unit_price_vnd >= 0),
    PRIMARY KEY (return_id, order_item_id)
);

CREATE TABLE public.return_evidence (
    image_id uuid PRIMARY KEY,
    return_id uuid NOT NULL REFERENCES public.return_request(return_id) ON DELETE CASCADE,
    content_type text NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png')),
    image_bytes bytea NOT NULL CHECK (octet_length(image_bytes) BETWEEN 1 AND 1048576),
    uploaded_at timestamptz NOT NULL
);
CREATE INDEX return_evidence_request_idx ON public.return_evidence(return_id, uploaded_at, image_id);

CREATE TABLE public.return_status_history (
    history_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    return_id uuid NOT NULL REFERENCES public.return_request(return_id) ON DELETE CASCADE,
    to_status text NOT NULL CHECK (to_status IN
        ('REQUESTED', 'MORE_IMAGES_REQUESTED', 'PICKUP_APPROVED', 'REJECTED', 'PENDING_REFUND', 'REFUNDED')),
    action text NOT NULL CHECK (action IN
        ('CREATE', 'ADD_EVIDENCE', 'REQUEST_IMAGES', 'APPROVE_PICKUP', 'REJECT', 'PASS_INSPECTION',
         'FAIL_INSPECTION', 'REFUND_COMPLETED')),
    actor_user_id uuid,
    reason text CHECK (char_length(reason) BETWEEN 1 AND 1000),
    changed_at timestamptz NOT NULL
);
CREATE INDEX return_status_history_request_idx ON public.return_status_history(return_id, history_id);

CREATE TABLE public.refund_result_inbox (
    refund_id uuid PRIMARY KEY,
    source_type text NOT NULL CHECK (source_type IN ('RETURN', 'CANCELLATION')),
    source_id uuid NOT NULL,
    order_id uuid NOT NULL REFERENCES public.customer_order(order_id),
    amount_vnd bigint NOT NULL CHECK (amount_vnd > 0),
    refunded_at timestamptz NOT NULL,
    UNIQUE (source_type, source_id)
);
