package com.stylemind.wardrobecommunity.client.commerce.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VariantLookupItem(
        @JsonProperty("variant_id") UUID variantId,
        @JsonProperty("product_id") UUID productId,
        @JsonProperty("product_name") String productName,
        @JsonProperty("product_status") String productStatus,
        @JsonProperty("variant_status") String variantStatus,
        String color,
        String size,
        @JsonProperty("unit_price_vnd") long unitPriceVnd,
        @JsonProperty("primary_image_url") String primaryImageUrl,
        boolean purchasable) {
}
