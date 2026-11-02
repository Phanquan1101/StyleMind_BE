package com.stylemind.commerce.dto.cart;

import java.util.UUID;

public record CartItem(
        UUID cartItemId,
        UUID cartId,
        UUID variantId,
        String sku,
        String productName,
        String brandName,
        String color,
        String size,
        String material,
        int quantity,
        long unitPriceVnd,
        long lineTotalVnd) {
}
