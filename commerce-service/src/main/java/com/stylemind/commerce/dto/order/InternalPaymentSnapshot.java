package com.stylemind.commerce.dto.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record InternalPaymentSnapshot(
        @JsonProperty("order_id") UUID orderId,
        @JsonProperty("customer_id") UUID customerId,
        @JsonProperty("payment_method") String paymentMethod,
        @JsonProperty("accepted_total_vnd") long acceptedTotalVnd,
        @JsonProperty("order_status") String orderStatus) {}
