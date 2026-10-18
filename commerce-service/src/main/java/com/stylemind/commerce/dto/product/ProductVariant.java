package com.stylemind.commerce.dto.product;

import com.stylemind.commerce.enums.ProductVariantStatus;
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
