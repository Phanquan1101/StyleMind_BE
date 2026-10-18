package com.stylemind.commerce.dto.order;

import com.stylemind.commerce.enums.OrderStatus;
import com.stylemind.commerce.enums.PaymentMethod;
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
