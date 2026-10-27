-- Physical mapping of the Payment service model.
-- Cross-service references to Commerce orders and Identity accounts are logical only.

CREATE TABLE public.payment_attempt (
    payment_attempt_id uuid PRIMARY KEY,
    order_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    payment_reference text NOT NULL UNIQUE,
    request_key text NOT NULL,
    payment_method text NOT NULL,
    expected_amount_vnd bigint NOT NULL,
    attempt_status text NOT NULL,
    generated_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone,
    provider_order_reference text,
    provider_bank_account_reference text,
    provider_virtual_account text,
    qr_payload text,
    transfer_content text,
    confirmed_at timestamp with time zone,
    CONSTRAINT payment_attempt_request_key_unique UNIQUE (order_id, request_key),
    CONSTRAINT payment_attempt_payment_method_check CHECK (payment_method IN ('COD', 'SEPAY_VIETQR')),
    CONSTRAINT payment_attempt_expected_amount_nonnegative CHECK (expected_amount_vnd >= 0),
    CONSTRAINT payment_attempt_status_check CHECK (
        attempt_status IN ('PENDING', 'SETTLED', 'EXPIRED', 'CANCELLED', 'REQUIRES_RECONCILIATION')
    ),
    CONSTRAINT payment_attempt_expiry_matches_method_check CHECK (
        (payment_method = 'COD' AND expires_at IS NULL)
        OR (payment_method = 'SEPAY_VIETQR' AND expires_at = generated_at + INTERVAL '15 minutes')
    )
);

CREATE TABLE public.financial_transaction (
    transaction_id uuid PRIMARY KEY,
    payment_attempt_id uuid NOT NULL UNIQUE REFERENCES public.payment_attempt (payment_attempt_id),
    actual_amount_vnd bigint NOT NULL,
    provider_transaction_reference text,
    provider_event_id text,
    provider_name text,
    recorded_by_user_id uuid,
    settled_at timestamp with time zone NOT NULL,
    CONSTRAINT financial_transaction_actual_amount_nonnegative CHECK (actual_amount_vnd >= 0)
);

CREATE UNIQUE INDEX financial_transaction_provider_reference_unique
    ON public.financial_transaction (provider_name, provider_transaction_reference)
    WHERE provider_name IS NOT NULL AND provider_transaction_reference IS NOT NULL;

CREATE UNIQUE INDEX financial_transaction_provider_event_unique
    ON public.financial_transaction (provider_name, provider_event_id)
    WHERE provider_name IS NOT NULL AND provider_event_id IS NOT NULL;

CREATE TABLE public.payment_exception (
    exception_id uuid PRIMARY KEY,
    payment_attempt_id uuid REFERENCES public.payment_attempt (payment_attempt_id),
    provider_name text NOT NULL,
    provider_event_id text NOT NULL,
    received_reference text,
    received_amount_vnd bigint NOT NULL,
    exception_reason text NOT NULL,
    reconciliation_status text NOT NULL,
    reviewed_by_user_id uuid,
    detected_at timestamp with time zone NOT NULL,
    CONSTRAINT payment_exception_provider_event_unique UNIQUE (provider_name, provider_event_id),
    CONSTRAINT payment_exception_received_amount_nonnegative CHECK (received_amount_vnd >= 0)
);

CREATE INDEX payment_attempt_order_id_index ON public.payment_attempt (order_id);
CREATE INDEX payment_attempt_customer_id_index ON public.payment_attempt (customer_id);
CREATE INDEX payment_exception_attempt_id_index ON public.payment_exception (payment_attempt_id);

CREATE TABLE public.payment_outbox (
    outbox_event_id uuid PRIMARY KEY,
    aggregate_type text NOT NULL,
    aggregate_id uuid NOT NULL,
    event_type text NOT NULL,
    event_payload text NOT NULL,
    created_at timestamp with time zone NOT NULL,
    published_at timestamp with time zone,
    CONSTRAINT payment_outbox_event_type_check CHECK (event_type IN ('PaymentConfirmed'))
);

CREATE INDEX payment_outbox_unpublished_index
    ON public.payment_outbox (created_at)
    WHERE published_at IS NULL;
