package com.stylemind.wardrobecommunity.dto.outfit;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OutfitSummaryResponse(
        @JsonProperty("outfit_id") UUID outfitId,
        String name,
        @JsonProperty("creation_source") String creationSource,
        @JsonProperty("item_count") long itemCount,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("updated_at") OffsetDateTime updatedAt,
        String visibility) {
}
