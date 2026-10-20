package com.stylemind.commerce.dto.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stylemind.commerce.dto.cart.CartItemResponse;
import java.util.List;

public record CheckoutPriceResponse(
        List<CartItemResponse> items,
        @JsonProperty("total_vnd") long totalVnd) {
}
