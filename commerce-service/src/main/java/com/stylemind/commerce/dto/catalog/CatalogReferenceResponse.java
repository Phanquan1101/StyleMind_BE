package com.stylemind.commerce.dto.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record CatalogReferenceResponse(
        @JsonProperty("id") UUID id,
        String name,
        String slug,
        @JsonProperty("is_active") boolean active) {
}
