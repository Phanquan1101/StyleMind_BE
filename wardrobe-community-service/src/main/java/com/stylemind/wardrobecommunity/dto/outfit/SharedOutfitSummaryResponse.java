package com.stylemind.wardrobecommunity.dto.outfit;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SharedOutfitSummaryResponse(
        @JsonProperty("outfit_id") UUID outfitId,
        String name,
        @JsonProperty("owner_display_name") String ownerDisplayName,
        @JsonProperty("item_count") long itemCount,
        @JsonProperty("granted_at") OffsetDateTime grantedAt) {
}
