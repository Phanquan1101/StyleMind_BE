package com.stylemind.commerce.model.product;

import java.util.UUID;

public record ProductCommand(
        UUID categoryId,
        UUID brandId,
        String name,
        String description,
        String demographicSegment,
        ProductStatus productStatus) {
}
