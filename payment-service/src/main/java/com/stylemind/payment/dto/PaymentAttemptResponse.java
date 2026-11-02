package com.stylemind.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PaymentAttemptResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("orderId") UUID orderId,
        @JsonProperty("method") String method,
        @JsonProperty("amountVnd") long amountVnd,
        @JsonProperty("reference") String reference,
        @JsonProperty("status") String status,
        @JsonProperty("expiresAt") Instant expiresAt,
        @JsonProperty("transferContent") String transferContent,
        @JsonProperty("qrPayload") String qrPayload) {
}
