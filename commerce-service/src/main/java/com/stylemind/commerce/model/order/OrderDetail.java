package com.stylemind.commerce.model.order;

import java.util.List;

public record OrderDetail(OrderSummary summary, AddressSnapshot address, List<OrderItem> items,
        List<OrderStatusHistoryEntry> history, CancellationRequest cancellationRequest) {
}
