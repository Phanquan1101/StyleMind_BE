package com.stylemind.commerce.dto.product;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VariantRequest(
        String sku,
        String size,
        String color,
        String material,
        @JsonProperty("unit_price_vnd") Long unitPriceVnd,
        @JsonProperty("variant_status") String variantStatus) {
}
