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
    public InternalPaymentSnapshotService(OrderRepository orders) { this.orders = orders; }

    @Transactional(readOnly = true)
    public InternalPaymentSnapshot get(UUID id) {
        var order = orders.findById(id).orElseThrow(() ->
                new ApiOperationException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order was not found"));
        return new InternalPaymentSnapshot(order.getOrderId(), order.getCustomerId(), order.getPaymentMethod(),
                order.getAcceptedTotalVnd(), order.getOrderStatus());
    }
}
