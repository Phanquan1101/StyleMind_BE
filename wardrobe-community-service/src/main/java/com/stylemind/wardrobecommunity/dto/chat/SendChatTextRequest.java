package com.stylemind.wardrobecommunity.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** OpenAPI shape; a local strict JSON reader validates the actual bytes. */
public record SendChatTextRequest(
        @JsonProperty("client_message_id") @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID clientMessageId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 4000) String content) {
}

