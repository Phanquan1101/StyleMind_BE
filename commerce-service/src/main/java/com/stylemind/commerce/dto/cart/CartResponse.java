package com.stylemind.commerce.dto.cart;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

public record CartResponse(
        @JsonProperty("cart_id") UUID cartId,
        @JsonProperty("customer_id") UUID customerId,
        @JsonProperty("guest_session_key") String guestSessionKey,
        @JsonProperty("cart_status") String cartStatus,
        List<CartItemResponse> items,
        @JsonProperty("total_vnd") long totalVnd) {
}
