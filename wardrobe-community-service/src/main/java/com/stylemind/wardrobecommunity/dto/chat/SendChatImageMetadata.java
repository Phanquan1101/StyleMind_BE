package com.stylemind.wardrobecommunity.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record SendChatImageMetadata(
        @JsonProperty("client_message_id") @Schema(requiredMode=Schema.RequiredMode.REQUIRED) UUID clientMessageId,
        @Schema(maxLength=4000, nullable=true, description="Omit or null for image-only; supplied caption must be nonblank") String content) { }
