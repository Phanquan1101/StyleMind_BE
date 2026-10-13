package com.stylemind.commerce.dto.product;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProductRequest(
        @JsonProperty("category_id") String categoryId,
        @JsonProperty("brand_id") String brandId,
        String name,
        String description,
        @JsonProperty("demographic_segment") String demographicSegment,
        @JsonProperty("product_status") String productStatus) {
}
