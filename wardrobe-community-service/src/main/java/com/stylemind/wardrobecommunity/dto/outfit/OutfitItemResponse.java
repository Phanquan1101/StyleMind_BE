package com.stylemind.wardrobecommunity.dto.outfit;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** Product fields are null and available is false when Commerce no longer returns the variant. */
public record OutfitItemResponse(
        @JsonProperty("outfit_item_id") UUID outfitItemId,
        @JsonProperty("variant_id") UUID variantId,
        @JsonProperty("display_order") int displayOrder,
        @JsonProperty("product_id") UUID productId,
        @JsonProperty("product_name") String productName,
        String color,
        String size,
        @JsonProperty("unit_price_vnd") Long unitPriceVnd,
        @JsonProperty("image_url") String imageUrl,
        boolean available) {
}
