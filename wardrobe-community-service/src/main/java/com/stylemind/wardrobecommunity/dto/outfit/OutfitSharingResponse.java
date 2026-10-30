package com.stylemind.wardrobecommunity.dto.outfit;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Owner-only settings; public token is never included in viewer DTOs. */
public record OutfitSharingResponse(
        @JsonProperty("outfit_id") UUID outfitId,
        String visibility,
        @JsonProperty("public_enabled") boolean publicEnabled,
        @JsonProperty("public_token") String publicToken,
        @JsonProperty("public_enabled_at") OffsetDateTime publicEnabledAt) {
    @Override
    public String toString() {
        return "OutfitSharingResponse[outfitId="
                + outfitId
                + ",visibility="
                + visibility
                + ",publicToken=REDACTED]";
    }
}
