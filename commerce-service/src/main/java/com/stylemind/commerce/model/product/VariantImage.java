package com.stylemind.commerce.model.product;

import java.util.UUID;

public record VariantImage(
        UUID imageId,
        UUID variantId,
        String imageUrl,
        String altText,
        int displayOrder,
        boolean primary) {
}
