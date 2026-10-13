package com.stylemind.commerce.model.catalog;

public record CatalogReferenceCommand(
        String name,
        String slug,
        boolean active) {
}
