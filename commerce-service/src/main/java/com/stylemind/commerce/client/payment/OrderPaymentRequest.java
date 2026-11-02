package com.stylemind.commerce.client.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stylemind.commerce.dto.order.OrderResponse;
import java.util.UUID;

public record OrderPaymentRequest(
        @JsonProperty("order_id") UUID orderId,
        @JsonProperty("customer_id") UUID customerId,
        @JsonProperty("payment_method") String paymentMethod,
        @JsonProperty("accepted_total_vnd") long acceptedTotalVnd,
        @JsonProperty("order_status") String orderStatus,
        @JsonProperty("request_key") String requestKey) {
    public static OrderPaymentRequest from(OrderResponse order) {
        return new OrderPaymentRequest(order.orderId(), order.customerId(), order.paymentMethod(),
                order.acceptedTotalVnd(), order.orderStatus(), "initial-" + order.orderId());
    }
}
