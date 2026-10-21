package com.stylemind.wardrobecommunity.dto.outfit;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ShareResponse(
        @JsonProperty("share_id") UUID shareId,
        String email,
        @JsonProperty("granted_at") OffsetDateTime grantedAt) {
}
