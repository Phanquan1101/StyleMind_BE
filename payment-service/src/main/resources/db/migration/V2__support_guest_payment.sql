-- Owner-approved guest payments are linked to Commerce orders, not Identity accounts.
ALTER TABLE public.payment_attempt
    ALTER COLUMN customer_id DROP NOT NULL;
