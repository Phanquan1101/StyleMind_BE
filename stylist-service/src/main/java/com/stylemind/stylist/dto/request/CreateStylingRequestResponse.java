package com.stylemind.stylist.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record CreateStylingRequestResponse(
        @JsonProperty("request_id") UUID requestId,
        @JsonProperty("session_id") UUID sessionId,
        @JsonProperty("source_message_id") UUID sourceMessageId,
        @JsonProperty("request_status") String requestStatus,
        @JsonProperty("requested_at") Instant requestedAt,
        @JsonProperty("generation_started") boolean generationStarted) {
}
