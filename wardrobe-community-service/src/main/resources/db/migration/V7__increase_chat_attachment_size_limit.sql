-- Owner-approved 2026-11-12: up to 10 MiB per private chat image.
ALTER TABLE public.chat_attachment
    DROP CONSTRAINT chat_attachment_size_check;

ALTER TABLE public.chat_attachment
    ADD CONSTRAINT chat_attachment_size_check
    CHECK (size_bytes BETWEEN 1 AND 10485760);
