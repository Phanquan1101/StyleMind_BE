package com.stylemind.commerce.model.product;

import java.util.UUID;

public record ProductVariant(
        UUID variantId,
        UUID productId,
        String sku,
        String size,
        String color,
        String material,
        long unitPriceVnd,
        ProductVariantStatus variantStatus) {
}
