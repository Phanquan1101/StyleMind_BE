package com.stylemind.commerce.dto.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        @JsonProperty("order_id") UUID orderId,
        @JsonProperty("order_number") String orderNumber,
        @JsonProperty("customer_id") UUID customerId,
        @JsonProperty("checkout_key") String checkoutKey,
        @JsonProperty("order_status") String orderStatus,
        @JsonProperty("payment_method") String paymentMethod,
        @JsonProperty("accepted_total_vnd") long acceptedTotalVnd,
        @JsonProperty("placed_at") OffsetDateTime placedAt,
        @JsonProperty("completed_at") OffsetDateTime completedAt,
        OrderAddressResponse address,
        List<OrderItemResponse> items,
        List<OrderHistoryResponse> history,
        @JsonProperty("cancellation_request") CancellationRequestResponse cancellationRequest) {
}
