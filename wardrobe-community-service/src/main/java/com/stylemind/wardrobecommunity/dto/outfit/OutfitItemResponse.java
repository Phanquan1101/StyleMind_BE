package com.stylemind.wardrobecommunity.dto.outfit;

import com.fasterxml.jackson.annotation.JsonInclude;
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
        boolean available,
        @JsonProperty("category_id") @JsonInclude(JsonInclude.Include.NON_NULL) UUID categoryId,
        @JsonProperty("category_name") @JsonInclude(JsonInclude.Include.NON_NULL) String categoryName) {

    /** Item without category data (shared/public views). */
    public OutfitItemResponse(UUID outfitItemId, UUID variantId, int displayOrder, UUID productId,
            String productName, String color, String size, Long unitPriceVnd, String imageUrl, boolean available) {
        this(outfitItemId, variantId, displayOrder, productId, productName, color, size, unitPriceVnd, imageUrl,
                available, null, null);
    }
}
