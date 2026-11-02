package com.stylemind.payment.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.stylemind.payment.model.PaymentMethod;
import java.util.UUID;

public record CreatePaymentAttemptRequest(
        @JsonProperty("orderId") @JsonAlias("order_id") UUID orderId,
        @JsonProperty("method") PaymentMethod method,
        @JsonProperty("requestKey") @JsonAlias("request_key") String requestKey) {
}
