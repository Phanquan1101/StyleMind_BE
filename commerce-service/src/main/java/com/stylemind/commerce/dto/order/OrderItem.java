package com.stylemind.commerce.dto.order;

import java.util.UUID;

public record OrderItem(
        UUID orderItemId,
        UUID variantId,
        String skuSnapshot,
        String productNameSnapshot,
        String brandNameSnapshot,
        String colorSnapshot,
        String sizeSnapshot,
        String materialSnapshot,
        String imageUrlSnapshot,
        int quantity,
        long unitPriceVnd,
        long lineTotalVnd) {
}
