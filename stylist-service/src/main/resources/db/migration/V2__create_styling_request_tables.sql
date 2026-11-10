CREATE TABLE public.styling_request (
    styling_request_id UUID PRIMARY KEY,
    stylist_session_id UUID NOT NULL,
    source_message_id UUID NULL,
    occasion TEXT NULL,
    intended_use TEXT NULL,
    style_context TEXT NULL,
    general_weather_context TEXT NULL,
    max_budget_vnd BIGINT NULL,
    budget_is_hard BOOLEAN NOT NULL DEFAULT FALSE,
    request_status TEXT NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_styling_request_session
        FOREIGN KEY (stylist_session_id)
        REFERENCES public.stylist_session (stylist_session_id),
    CONSTRAINT fk_styling_request_source_message
        FOREIGN KEY (stylist_session_id, source_message_id)
        REFERENCES public.stylist_message (stylist_session_id, stylist_message_id),
    CONSTRAINT ck_styling_request_budget_nonnegative
        CHECK (max_budget_vnd IS NULL OR max_budget_vnd >= 0),
    CONSTRAINT ck_styling_request_hard_budget_has_amount
        CHECK (budget_is_hard = FALSE OR max_budget_vnd IS NOT NULL),
    CONSTRAINT ck_styling_request_status_received
        CHECK (request_status = 'RECEIVED')
);

CREATE INDEX idx_styling_request_session
    ON public.styling_request (stylist_session_id);

CREATE TABLE public.styling_constraint (
    constraint_id UUID PRIMARY KEY,
    styling_request_id UUID NOT NULL,
    constraint_kind TEXT NOT NULL,
    catalog_entity_id UUID NULL,
    text_value TEXT NULL,
    is_hard BOOLEAN NOT NULL,
    CONSTRAINT fk_styling_constraint_request
        FOREIGN KEY (styling_request_id)
        REFERENCES public.styling_request (styling_request_id),
    CONSTRAINT ck_styling_constraint_kind
        CHECK (constraint_kind IN (
            'EXCLUDE_CATEGORY', 'EXCLUDE_COLOR', 'EXCLUDE_PRODUCT', 'EXCLUDE_VARIANT')),
    CONSTRAINT ck_styling_constraint_one_value
        CHECK ((catalog_entity_id IS NOT NULL) <> (text_value IS NOT NULL)),
    CONSTRAINT ck_styling_constraint_value_matches_kind
        CHECK ((constraint_kind = 'EXCLUDE_COLOR' AND text_value IS NOT NULL AND catalog_entity_id IS NULL)
            OR (constraint_kind <> 'EXCLUDE_COLOR' AND catalog_entity_id IS NOT NULL AND text_value IS NULL))
);

CREATE INDEX idx_styling_constraint_request
    ON public.styling_constraint (styling_request_id);
