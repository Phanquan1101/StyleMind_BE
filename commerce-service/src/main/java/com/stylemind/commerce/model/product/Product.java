package com.stylemind.commerce.model.product;

import java.util.UUID;

public record Product(
        UUID productId,
        UUID categoryId,
        UUID brandId,
        String categoryName,
        String brandName,
        String name,
        String description,
        String demographicSegment,
        ProductStatus productStatus) {
}
