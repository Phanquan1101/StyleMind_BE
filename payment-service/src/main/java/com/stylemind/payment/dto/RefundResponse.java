package com.stylemind.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record RefundResponse(
        @JsonProperty("refund_id") UUID refundId,
        @JsonProperty("source_type") RefundSourceType sourceType,
        @JsonProperty("source_id") UUID sourceId,
        @JsonProperty("order_id") UUID orderId,
        @JsonProperty("amount_vnd") long amountVnd,
        String status,
        @JsonProperty("transfer_reference") String transferReference,
        @JsonProperty("failure_reason") String failureReason,
        @JsonProperty("refunded_at") Instant refundedAt,
        @JsonProperty("updated_at") Instant updatedAt,
        @JsonProperty("has_receipt") boolean hasReceipt) {
}
