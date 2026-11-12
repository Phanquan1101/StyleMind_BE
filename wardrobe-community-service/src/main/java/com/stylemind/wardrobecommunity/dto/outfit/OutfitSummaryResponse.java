package com.stylemind.wardrobecommunity.dto.outfit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OutfitSummaryResponse(
        @JsonProperty("outfit_id") UUID outfitId,
        String name,
        @JsonProperty("creation_source") String creationSource,
        @JsonProperty("item_count") long itemCount,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("updated_at") OffsetDateTime updatedAt,
        String visibility,
        @JsonProperty("category_counts") @JsonInclude(JsonInclude.Include.NON_NULL)
        List<OutfitCategoryCount> categoryCounts) {

    /** Summary without category counts (rename and other single-outfit answers). */
    public OutfitSummaryResponse(UUID outfitId, String name, String creationSource, long itemCount,
            OffsetDateTime createdAt, OffsetDateTime updatedAt, String visibility) {
        this(outfitId, name, creationSource, itemCount, createdAt, updatedAt, visibility, null);
    }
}
