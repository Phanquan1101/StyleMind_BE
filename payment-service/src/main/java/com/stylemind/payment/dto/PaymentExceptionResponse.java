package com.stylemind.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record PaymentExceptionResponse(
        @JsonProperty("exception_id") UUID exceptionId,
        @JsonProperty("payment_attempt_id") UUID paymentAttemptId,
        @JsonProperty("provider_name") String providerName,
        @JsonProperty("provider_event_id") String providerEventId,
        @JsonProperty("received_reference") String receivedReference,
        @JsonProperty("received_amount_vnd") long receivedAmountVnd,
        @JsonProperty("exception_reason") String exceptionReason,
        @JsonProperty("reconciliation_status") String reconciliationStatus,
        @JsonProperty("reviewed_by_user_id") UUID reviewedByUserId,
        @JsonProperty("detected_at") Instant detectedAt) {
}
