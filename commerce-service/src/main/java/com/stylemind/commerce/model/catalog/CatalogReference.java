package com.stylemind.commerce.model.catalog;

import java.util.UUID;

public record CatalogReference(
        UUID id,
        String name,
        String slug,
        boolean active) {
}
