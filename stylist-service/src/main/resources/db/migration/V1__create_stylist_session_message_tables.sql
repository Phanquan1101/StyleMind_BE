CREATE TABLE public.stylist_session (
    stylist_session_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    title TEXT NULL,
    opened_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ NULL,
    CONSTRAINT ck_stylist_session_closed_after_open
        CHECK (closed_at IS NULL OR closed_at >= opened_at)
);

CREATE INDEX idx_stylist_session_customer_session
    ON public.stylist_session (customer_id, stylist_session_id);

CREATE TABLE public.stylist_message (
    stylist_message_id UUID PRIMARY KEY,
    stylist_session_id UUID NOT NULL,
    speaker_role TEXT NOT NULL,
    content TEXT NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_stylist_message_session
        FOREIGN KEY (stylist_session_id)
        REFERENCES public.stylist_session (stylist_session_id),
    CONSTRAINT ck_stylist_message_speaker_role
        CHECK (speaker_role IN ('USER', 'ASSISTANT')),
    CONSTRAINT uq_stylist_message_session_message
        UNIQUE (stylist_session_id, stylist_message_id)
);

CREATE INDEX idx_stylist_message_session_sent_message
    ON public.stylist_message (stylist_session_id, sent_at DESC, stylist_message_id DESC);
