package com.stylemind.commerce.model.cart;

import java.util.List;
import java.util.UUID;

public record Cart(UUID cartId, UUID customerId, String guestSessionKey, String cartStatus, List<CartItem> items) {

    public long totalVnd() {
        return items.stream().mapToLong(CartItem::lineTotalVnd).sum();
    }
}
