package com.stylemind.commerce.dto.product;

import com.stylemind.commerce.enums.ProductVariantStatus;
public record VariantCommand(
        String sku,
        String size,
        String color,
        String material,
        long unitPriceVnd,
        ProductVariantStatus variantStatus) {
}
