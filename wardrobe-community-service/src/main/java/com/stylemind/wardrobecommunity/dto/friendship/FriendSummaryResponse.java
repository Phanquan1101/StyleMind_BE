package com.stylemind.wardrobecommunity.dto.friendship;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public record FriendSummaryResponse(
        @JsonProperty("friendship_id") @Schema(example = "10000000-0000-0000-0000-000000000001") UUID friendshipId,
        @JsonProperty("friend_account_id") @Schema(example = "20000000-0000-0000-0000-000000000002") UUID friendAccountId,
        @JsonProperty("display_name") @Schema(nullable = true, description = "Current Identity display name, if available", example = "Minh")
        String displayName,
        @JsonProperty("established_at") @Schema(example = "2026-10-24T09:00:00Z") OffsetDateTime establishedAt) {
}
