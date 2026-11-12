package com.stylemind.wardrobecommunity.dto.outfit;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** How many items of an outfit belong to one catalog category. */
public record OutfitCategoryCount(
        @JsonProperty("category_id") UUID categoryId,
        @JsonProperty("category_name") String categoryName,
        long count) {
}
