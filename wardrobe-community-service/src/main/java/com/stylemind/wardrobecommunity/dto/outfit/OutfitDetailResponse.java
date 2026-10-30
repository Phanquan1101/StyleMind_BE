package com.stylemind.wardrobecommunity.dto.outfit;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OutfitDetailResponse(
        @JsonProperty("outfit_id") UUID outfitId,
        String name,
        @JsonProperty("creation_source") String creationSource,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("updated_at") OffsetDateTime updatedAt,
        List<OutfitItemResponse> items,
        @JsonProperty("public_token") String publicToken,
        String visibility) {
}
