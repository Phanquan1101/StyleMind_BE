package com.stylemind.commerce.dto.cart;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record CartItemResponse(
        @JsonProperty("cart_item_id") UUID cartItemId,
        @JsonProperty("variant_id") UUID variantId,
        String sku,
        @JsonProperty("product_name") String productName,
        @JsonProperty("brand_name") String brandName,
        String color,
        String size,
        String material,
        int quantity,
        @JsonProperty("unit_price_vnd") long unitPriceVnd,
        @JsonProperty("line_total_vnd") long lineTotalVnd) {
}
