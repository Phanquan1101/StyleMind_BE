package com.stylemind.commerce.dto.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record CatalogReferenceResponse(
        @JsonProperty("id") UUID id,
        String name,
        String slug,
        @JsonProperty("is_active") boolean active,
        /** Number of products (any status) in a category; only set on the admin category list. */
        @JsonProperty("product_count") @JsonInclude(JsonInclude.Include.NON_NULL) Long productCount) {

    public CatalogReferenceResponse(UUID id, String name, String slug, boolean active) {
        this(id, name, slug, active, null);
    }
}
