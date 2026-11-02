package com.stylemind.commerce.dto.cart;

import java.util.UUID;

public record CartOwner(UUID customerId, String guestSessionKey) {

    public static CartOwner customer(UUID customerId) {
        return new CartOwner(customerId, null);
    }

    public static CartOwner guest(String guestSessionKey) {
        return new CartOwner(null, guestSessionKey);
    }

    public boolean customer() {
        return customerId != null;
    }
}
