package com.stylemind.commerce.model.order;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderSummary(
        UUID orderId,
        String orderNumber,
        UUID customerId,
        String checkoutKey,
        OrderStatus orderStatus,
        PaymentMethod paymentMethod,
        long acceptedTotalVnd,
        OffsetDateTime placedAt,
        OffsetDateTime completedAt) {
}
