package com.stylemind.stylist.dto.generation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GenerateRecommendationsResponse(
        @JsonProperty("request_id") UUID requestId,
        @JsonProperty("request_status") String requestStatus,
        List<Recommendation> recommendations,
        List<String> limitations) {
    public GenerateRecommendationsResponse {
        recommendations = List.copyOf(recommendations);
        limitations = List.copyOf(limitations);
    }

    public record Recommendation(
            @JsonProperty("recommendation_id") UUID recommendationId,
            String explanation,
            @JsonProperty("styling_tips") String stylingTips,
            @JsonProperty("generated_at") Instant generatedAt,
            List<Item> items) {
        public Recommendation { items = List.copyOf(items); }
    }

    public record Item(
            @JsonProperty("source_type") String sourceType,
            @JsonProperty("shop_variant_id") UUID shopVariantId,
            @JsonProperty("slot_name") String slotName,
            @JsonProperty("selection_reason") String selectionReason,
            @JsonProperty("display_order") int displayOrder) { }
}
