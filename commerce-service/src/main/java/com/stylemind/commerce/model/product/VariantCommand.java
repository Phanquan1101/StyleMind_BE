package com.stylemind.commerce.model.product;

public record VariantCommand(
        String sku,
        String size,
        String color,
        String material,
        long unitPriceVnd,
        ProductVariantStatus variantStatus) {
}
