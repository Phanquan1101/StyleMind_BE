package com.stylemind.stylist.dto.generation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stylemind.stylist.client.commerce.CommerceCatalogClient.CatalogImage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RecommendationDetailResponse(
        @JsonProperty("recommendation_id") UUID recommendationId,
        @JsonProperty("request_id") UUID requestId,
        String explanation,
        @JsonProperty("styling_tips") String stylingTips,
        @JsonProperty("generated_at") Instant generatedAt,
        @JsonProperty("total_current_price_vnd") Long totalCurrentPriceVnd,
        @JsonProperty("price_semantics") String priceSemantics,
        List<Item> items) {
    public RecommendationDetailResponse { items = List.copyOf(items); }

    public record Item(
            @JsonProperty("source_type") String sourceType,
            @JsonProperty("wardrobe_item_id") UUID wardrobeItemId,
            @JsonProperty("shop_variant_id") UUID shopVariantId,
            @JsonProperty("slot_name") String slotName,
            @JsonProperty("selection_reason") String selectionReason,
            @JsonProperty("display_order") int displayOrder,
            @JsonProperty("current_availability") String currentAvailability,
            @JsonProperty("product_id") UUID productId,
            @JsonProperty("category_id") UUID categoryId,
            @JsonProperty("brand_id") UUID brandId,
            @JsonProperty("product_name") String productName,
            @JsonProperty("category_name") String categoryName,
            @JsonProperty("brand_name") String brandName,
            String color,
            String size,
            String material,
            @JsonProperty("current_unit_price_vnd") Long currentUnitPriceVnd,
            List<CatalogImage> images) {
        public Item { images = images == null ? List.of() : List.copyOf(images); }
    }
}
