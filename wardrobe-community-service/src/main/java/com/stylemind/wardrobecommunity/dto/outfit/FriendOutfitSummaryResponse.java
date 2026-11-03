package com.stylemind.wardrobecommunity.dto.outfit;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FriendOutfitSummaryResponse(
        @JsonProperty("outfit_id") UUID outfitId,
        String name,
        @JsonProperty("owner_display_name") String ownerDisplayName,
        @JsonProperty("item_count") long itemCount,
        String visibility,
        @JsonProperty("updated_at") OffsetDateTime updatedAt) {}
