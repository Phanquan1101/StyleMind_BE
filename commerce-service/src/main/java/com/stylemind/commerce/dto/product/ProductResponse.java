package com.stylemind.commerce.dto.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

public record ProductResponse(
        @JsonProperty("product_id") UUID productId,
        @JsonProperty("category_id") UUID categoryId,
        @JsonProperty("brand_id") UUID brandId,
        @JsonProperty("category_name") String categoryName,
        @JsonProperty("brand_name") String brandName,
        String name,
        String description,
        @JsonProperty("demographic_segment") String demographicSegment,
        @JsonProperty("product_status") String productStatus,
        List<VariantResponse> variants) {
}
