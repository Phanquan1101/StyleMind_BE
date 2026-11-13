package com.stylemind.commerce.service.order;

import com.stylemind.commerce.dto.order.InternalPaymentSnapshot;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.repository.order.OrderRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InternalPaymentSnapshotService {
    private final OrderRepository orders;
    private final com.stylemind.commerce.repository.order.OrderHistoryRepository history;
    public InternalPaymentSnapshotService(OrderRepository orders,
            com.stylemind.commerce.repository.order.OrderHistoryRepository history) {
        this.orders = orders;
        this.history = history;
    }

    @Transactional(readOnly = true)
    public InternalPaymentSnapshot get(UUID id) {
        var order = orders.findById(id).orElseThrow(() ->
                new ApiOperationException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order was not found"));
        var intent = history.findByOrderIdOrderByChangedAtAscHistoryIdAsc(id).stream()
                .filter(entry -> "COD".equals(order.getPaymentMethod()) && "COMPLETED".equals(order.getOrderStatus())
                        && "SHIPPED".equals(entry.getFromStatus()) && "COMPLETED".equals(entry.getToStatus())
                        && entry.getActorUserId() != null && entry.getReason() != null
                        && (entry.getReason().equals(CodDeliveryRequested.HISTORY_PREFIX)
                            || entry.getReason().startsWith(CodDeliveryRequested.HISTORY_PREFIX + " | ")))
                .findFirst();
        return new InternalPaymentSnapshot(order.getOrderId(), order.getCustomerId(), order.getPaymentMethod(),
                order.getAcceptedTotalVnd(), order.getOrderStatus(),
                intent.map(entry -> entry.getActorUserId()).orElse(null),
                intent.map(entry -> entry.getChangedAt().toInstant()).orElse(null));
    }
}
