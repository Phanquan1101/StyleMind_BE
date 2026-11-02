package com.stylemind.commerce.dto.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CancellationRequestResponse(
        @JsonProperty("cancellation_request_id") UUID cancellationRequestId,
        @JsonProperty("order_id") UUID orderId,
        @JsonProperty("requested_by_user_id") UUID requestedByUserId,
        String reason,
        @JsonProperty("request_status") String requestStatus,
        @JsonProperty("requested_at") OffsetDateTime requestedAt,
        @JsonProperty("reviewed_by_user_id") UUID reviewedByUserId,
        @JsonProperty("reviewed_at") OffsetDateTime reviewedAt,
        @JsonProperty("decision_reason") String decisionReason) {
}
