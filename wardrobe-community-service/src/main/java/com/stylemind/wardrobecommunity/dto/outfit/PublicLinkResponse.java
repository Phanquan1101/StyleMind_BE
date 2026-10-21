package com.stylemind.wardrobecommunity.dto.outfit;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record PublicLinkResponse(
        @JsonProperty("public_token") String publicToken,
        @JsonProperty("enabled_at") OffsetDateTime enabledAt) {
}
