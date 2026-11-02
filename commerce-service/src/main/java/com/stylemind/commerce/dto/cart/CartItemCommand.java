package com.stylemind.commerce.dto.cart;

import java.util.UUID;

public record CartItemCommand(UUID variantId, int quantity, String guestSessionKey) {
}
