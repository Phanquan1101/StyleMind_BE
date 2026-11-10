package com.stylemind.stylist.dto.session;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record StylistSessionResponse(
        @JsonProperty("session_id") UUID sessionId,
        String title,
        @JsonProperty("opened_at") Instant openedAt,
        @JsonProperty("closed_at") Instant closedAt) {
}
