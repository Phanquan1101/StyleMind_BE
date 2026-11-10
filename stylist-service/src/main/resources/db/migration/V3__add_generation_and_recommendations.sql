ALTER TABLE public.styling_request
    DROP CONSTRAINT ck_styling_request_status_received,
    ADD CONSTRAINT ck_styling_request_status
        CHECK (request_status IN ('RECEIVED', 'PROCESSING', 'SUCCEEDED', 'NO_RESULT', 'FAILED'));

CREATE TABLE public.wardrobe_recommendation (
    recommendation_id UUID PRIMARY KEY,
    styling_request_id UUID NOT NULL,
    explanation TEXT NOT NULL,
    styling_tips TEXT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_wardrobe_recommendation_request
        FOREIGN KEY (styling_request_id) REFERENCES public.styling_request (styling_request_id),
    CONSTRAINT ck_wardrobe_recommendation_explanation
        CHECK (length(btrim(explanation)) BETWEEN 1 AND 2000),
    CONSTRAINT ck_wardrobe_recommendation_tips
        CHECK (styling_tips IS NULL OR length(btrim(styling_tips)) BETWEEN 1 AND 1000)
);

CREATE INDEX idx_wardrobe_recommendation_request_generated
    ON public.wardrobe_recommendation (styling_request_id, generated_at, recommendation_id);

CREATE TABLE public.recommendation_item (
    recommendation_item_id UUID PRIMARY KEY,
    recommendation_id UUID NOT NULL,
    source_type TEXT NOT NULL,
    wardrobe_item_id UUID NULL,
    shop_variant_id UUID NULL,
    slot_name TEXT NOT NULL,
    selection_reason TEXT NOT NULL,
    display_order INTEGER NOT NULL,
    CONSTRAINT fk_recommendation_item_recommendation
        FOREIGN KEY (recommendation_id) REFERENCES public.wardrobe_recommendation (recommendation_id),
    CONSTRAINT ck_recommendation_item_source
        CHECK ((source_type = 'OWNED_WARDROBE' AND wardrobe_item_id IS NOT NULL AND shop_variant_id IS NULL)
            OR (source_type = 'SHOP_VARIANT' AND wardrobe_item_id IS NULL AND shop_variant_id IS NOT NULL)),
    CONSTRAINT ck_recommendation_item_slot
        CHECK (slot_name IN ('TOP', 'BOTTOM')),
    CONSTRAINT ck_recommendation_item_reason
        CHECK (length(btrim(selection_reason)) BETWEEN 1 AND 500),
    CONSTRAINT ck_recommendation_item_display_order
        CHECK (display_order IN (1, 2)),
    CONSTRAINT uq_recommendation_item_slot
        UNIQUE (recommendation_id, slot_name),
    CONSTRAINT uq_recommendation_item_order
        UNIQUE (recommendation_id, display_order),
    CONSTRAINT uq_recommendation_item_shop_variant
        UNIQUE (recommendation_id, shop_variant_id)
);

CREATE INDEX idx_recommendation_item_recommendation
    ON public.recommendation_item (recommendation_id, display_order);
