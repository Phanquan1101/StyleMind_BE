package com.stylemind.commerce.dto.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderHistoryResponse(
        @JsonProperty("history_id") UUID historyId,
        @JsonProperty("from_status") String fromStatus,
        @JsonProperty("to_status") String toStatus,
        @JsonProperty("actor_user_id") UUID actorUserId,
        @JsonProperty("changed_at") OffsetDateTime changedAt,
        String reason) {
}
