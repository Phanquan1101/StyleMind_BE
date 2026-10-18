package com.stylemind.commerce.model.cart;

import java.util.UUID;

public record VariantPurchaseInfo(
        UUID variantId,
        String sku,
        String productName,
        String brandName,
        String color,
        String size,
        String material,
        long unitPriceVnd,
        String imageUrl) {
}
