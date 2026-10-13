package com.stylemind.commerce.dto.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

public record VariantResponse(
        @JsonProperty("variant_id") UUID variantId,
        @JsonProperty("product_id") UUID productId,
        String sku,
        String size,
        String color,
        String material,
        @JsonProperty("unit_price_vnd") long unitPriceVnd,
        @JsonProperty("variant_status") String variantStatus,
        List<VariantImageResponse> images) {
}
