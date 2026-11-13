ALTER TABLE public.qr_checkout
    ADD COLUMN payment_method text NOT NULL DEFAULT 'SEPAY_VIETQR'
    CONSTRAINT qr_checkout_payment_method_check CHECK (payment_method IN ('SEPAY_VIETQR', 'VNPAY'));
