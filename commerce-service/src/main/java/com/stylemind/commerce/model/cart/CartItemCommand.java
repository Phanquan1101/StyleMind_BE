package com.stylemind.commerce.model.cart;

import java.util.UUID;

public record CartItemCommand(UUID variantId, int quantity, String guestSessionKey) {
}
