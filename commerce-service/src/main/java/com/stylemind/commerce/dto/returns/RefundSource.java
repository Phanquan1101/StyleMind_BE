package com.stylemind.commerce.dto.returns;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RefundSource(
        @JsonProperty("source_type") Type sourceType,
        @JsonProperty("source_id") UUID sourceId,
        @JsonProperty("order_id") UUID orderId,
        @JsonProperty("customer_id") UUID customerId,
        @JsonProperty("payment_attempt_id") UUID paymentAttemptId,
        @JsonProperty("amount_vnd") long amountVnd) {

    public enum Type { RETURN, CANCELLATION }

    public record Result(UUID refundId, Type sourceType, UUID sourceId, UUID orderId,
                         long amountVnd, OffsetDateTime refundedAt) { }
}
