package com.stylemind.wardrobecommunity.dto.outfit;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

/** What a viewer may see of someone else's outfit: no owner account id or email, source, public token or shares. */
public record SharedOutfitViewResponse(
        @JsonProperty("outfit_id") UUID outfitId,
        String name,
        @JsonProperty("owner_display_name") String ownerDisplayName,
        List<OutfitItemResponse> items) {
}
