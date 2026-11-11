CREATE TABLE public.refund_case (
    refund_id uuid PRIMARY KEY,
    source_type text NOT NULL CHECK (source_type IN ('RETURN', 'CANCELLATION')),
    source_id uuid NOT NULL,
    order_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    transaction_id uuid NOT NULL REFERENCES public.financial_transaction (transaction_id),
    amount_vnd bigint NOT NULL CHECK (amount_vnd > 0),
    status text NOT NULL CHECK (status IN ('AWAITING_DETAILS', 'PENDING', 'PROCESSING', 'REFUNDED', 'FAILED')),
    destination_ciphertext bytea,
    assigned_admin_id uuid,
    transfer_reference text UNIQUE,
    proof_reference text,
    failure_reason text,
    refunded_at timestamp with time zone,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT refund_case_source_unique UNIQUE (source_type, source_id),
    CONSTRAINT refund_case_destination_check CHECK ((status = 'AWAITING_DETAILS') = (destination_ciphertext IS NULL)),
    CONSTRAINT refund_case_admin_check CHECK ((status IN ('PROCESSING', 'REFUNDED', 'FAILED')) = (assigned_admin_id IS NOT NULL)),
    CONSTRAINT refund_case_failure_check CHECK ((status = 'FAILED') = (failure_reason IS NOT NULL)),
    CONSTRAINT refund_case_completion_check CHECK (
        (status = 'REFUNDED' AND refunded_at IS NOT NULL AND transfer_reference IS NOT NULL AND proof_reference IS NOT NULL)
        OR (status <> 'REFUNDED' AND refunded_at IS NULL AND transfer_reference IS NULL AND proof_reference IS NULL)
    )
);

CREATE INDEX refund_case_transaction_idx ON public.refund_case (transaction_id);

CREATE TABLE public.refund_audit (
    audit_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    refund_id uuid NOT NULL REFERENCES public.refund_case (refund_id),
    actor_id uuid NOT NULL,
    action text NOT NULL CHECK (action IN ('PREPARED', 'DESTINATION_SAVED', 'STARTED', 'RETRIED', 'DESTINATION_VIEWED', 'REFUNDED', 'FAILED')),
    reason text,
    recorded_at timestamp with time zone NOT NULL,
    CONSTRAINT refund_audit_reason_check CHECK ((action = 'FAILED') = (reason IS NOT NULL))
);

CREATE INDEX refund_audit_case_idx ON public.refund_audit (refund_id, audit_id);

CREATE TABLE public.refund_result_outbox (
    refund_id uuid PRIMARY KEY REFERENCES public.refund_case (refund_id),
    next_attempt_at timestamp with time zone NOT NULL,
    published_at timestamp with time zone
);

CREATE INDEX refund_result_outbox_pending_idx ON public.refund_result_outbox (next_attempt_at, refund_id)
    WHERE published_at IS NULL;
