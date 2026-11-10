package com.stylemind.commerce.dto.order;

import java.util.List;
import java.util.UUID;

public record CheckoutSelection(List<UUID> cartItemIds, UUID variantId, int quantity) {
    public boolean buyNow() { return variantId != null; }
}
