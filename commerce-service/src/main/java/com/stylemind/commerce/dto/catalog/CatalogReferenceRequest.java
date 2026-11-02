package com.stylemind.commerce.dto.catalog;

public record CatalogReferenceRequest(
        String name,
        String slug,
        Boolean is_active) {
}
