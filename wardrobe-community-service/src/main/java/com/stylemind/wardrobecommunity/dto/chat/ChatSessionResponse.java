package com.stylemind.wardrobecommunity.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ChatSessionResponse(
        @JsonProperty("chat_session_id") @Schema(example = "30000000-0000-0000-0000-000000000003") UUID chatSessionId,
        @JsonProperty("friend_account_id") @Schema(example = "20000000-0000-0000-0000-000000000002") UUID friendAccountId,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("updated_at") OffsetDateTime updatedAt) {
}

