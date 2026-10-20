package com.stylemind.commerce.dto.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record OrderItemResponse(
        @JsonProperty("order_item_id") UUID orderItemId,
        @JsonProperty("variant_id") UUID variantId,
        @JsonProperty("sku_snapshot") String skuSnapshot,
        @JsonProperty("product_name_snapshot") String productNameSnapshot,
        @JsonProperty("brand_name_snapshot") String brandNameSnapshot,
        @JsonProperty("color_snapshot") String colorSnapshot,
        @JsonProperty("size_snapshot") String sizeSnapshot,
        @JsonProperty("material_snapshot") String materialSnapshot,
        @JsonProperty("image_url_snapshot") String imageUrlSnapshot,
        int quantity,
        @JsonProperty("unit_price_vnd") long unitPriceVnd,
        @JsonProperty("line_total_vnd") long lineTotalVnd) {
}
