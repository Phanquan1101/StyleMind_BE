ALTER TABLE public.customer_order
    DROP CONSTRAINT customer_order_payment_method_check,
    ADD CONSTRAINT customer_order_payment_method_check
        CHECK (payment_method IN ('COD', 'SEPAY_VIETQR', 'VNPAY'));

ALTER TABLE public.payment_event_inbox
    DROP CONSTRAINT payment_event_inbox_payment_method_check,
    ADD CONSTRAINT payment_event_inbox_payment_method_check
        CHECK (payment_method IN ('SEPAY_VIETQR', 'COD', 'VNPAY'));
