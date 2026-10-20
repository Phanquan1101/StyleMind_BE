package com.stylemind.commerce.dto.product;

import com.stylemind.commerce.enums.ProductStatus;
import java.util.UUID;

public record ProductCommand(
        UUID categoryId,
        UUID brandId,
        String name,
        String description,
        String demographicSegment,
        ProductStatus productStatus) {
}
