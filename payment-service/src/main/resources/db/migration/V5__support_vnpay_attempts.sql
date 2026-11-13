ALTER TABLE public.payment_attempt
    DROP CONSTRAINT payment_attempt_payment_method_check,
    DROP CONSTRAINT payment_attempt_expiry_matches_method_check;

ALTER TABLE public.payment_attempt
    ADD CONSTRAINT payment_attempt_payment_method_check
        CHECK (payment_method IN ('COD', 'SEPAY_VIETQR', 'VNPAY')),
    ADD CONSTRAINT payment_attempt_expiry_matches_method_check CHECK (
        (payment_method = 'COD' AND expires_at IS NULL)
        OR (payment_method IN ('SEPAY_VIETQR', 'VNPAY') AND expires_at = generated_at + INTERVAL '15 minutes')
    );
