CREATE TABLE public.cancellation_refund_obligation (
    cancellation_request_id uuid PRIMARY KEY REFERENCES public.cancellation_request(cancellation_request_id),
    order_id uuid NOT NULL UNIQUE REFERENCES public.customer_order(order_id),
    payment_event_id uuid NOT NULL UNIQUE REFERENCES public.payment_event_inbox(event_id),
    amount_vnd bigint NOT NULL CHECK (amount_vnd > 0),
    obligation_status text NOT NULL CHECK (obligation_status = 'PENDING_REFUND'),
    created_at timestamptz NOT NULL
);
