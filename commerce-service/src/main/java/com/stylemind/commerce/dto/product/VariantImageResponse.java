package com.stylemind.commerce.dto.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record VariantImageResponse(
        @JsonProperty("image_id") UUID imageId,
        @JsonProperty("variant_id") UUID variantId,
        @JsonProperty("image_url") String imageUrl,
        @JsonProperty("alt_text") String altText,
        @JsonProperty("display_order") int displayOrder,
        @JsonProperty("is_primary") boolean primary) {
}
