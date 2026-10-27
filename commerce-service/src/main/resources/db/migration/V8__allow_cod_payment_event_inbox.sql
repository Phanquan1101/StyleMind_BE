-- US-04.03: owner-approved extension; preserve V7 and all existing inbox data.
ALTER TABLE public.payment_event_inbox
    DROP CONSTRAINT payment_event_inbox_payment_method_check;
ALTER TABLE public.payment_event_inbox
    ADD CONSTRAINT payment_event_inbox_payment_method_check
    CHECK (payment_method IN ('SEPAY_VIETQR', 'COD'));
