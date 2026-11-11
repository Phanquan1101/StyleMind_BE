CREATE TABLE public.refund_transfer_receipt (
    refund_id uuid PRIMARY KEY REFERENCES public.refund_case(refund_id),
    content_type text NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png')),
    content bytea NOT NULL CHECK (octet_length(content) BETWEEN 1 AND 5242880),
    content_sha256 text NOT NULL CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    uploaded_by_user_id uuid NOT NULL,
    uploaded_at timestamptz NOT NULL
);
