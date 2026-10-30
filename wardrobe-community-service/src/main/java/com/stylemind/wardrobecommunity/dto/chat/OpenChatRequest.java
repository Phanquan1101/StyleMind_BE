package com.stylemind.wardrobecommunity.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** OpenAPI request shape; the route validates the raw JSON strictly before deriving this peer ID. */
public record OpenChatRequest(
        @JsonProperty("friend_account_id") @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                example = "20000000-0000-0000-0000-000000000002") UUID friendAccountId) {
}

