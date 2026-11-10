package com.stylemind.stylist.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StylingRequestResponse(
        @JsonProperty("request_id") UUID requestId,
        @JsonProperty("session_id") UUID sessionId,
        @JsonProperty("source_message_id") UUID sourceMessageId,
        @JsonProperty("request_status") String requestStatus,
        @JsonProperty("requested_at") Instant requestedAt,
        @JsonProperty("generation_started") boolean generationStarted,
        String occasion,
        @JsonProperty("intended_use") String intendedUse,
        @JsonProperty("style_context") String styleContext,
        @JsonProperty("general_weather_context") String generalWeatherContext,
        @JsonProperty("max_budget_vnd") Long maxBudgetVnd,
        @JsonProperty("budget_is_hard") boolean budgetIsHard,
        List<ConstraintResponse> constraints) {

    public record ConstraintResponse(
            @JsonProperty("constraint_kind") String constraintKind,
            @JsonProperty("catalog_entity_id") UUID catalogEntityId,
            @JsonProperty("text_value") String textValue,
            @JsonProperty("is_hard") boolean hard) {
    }
}
