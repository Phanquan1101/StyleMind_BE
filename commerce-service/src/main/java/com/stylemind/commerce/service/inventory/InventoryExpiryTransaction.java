package com.stylemind.commerce.service.inventory;

import com.stylemind.commerce.service.order.VariantQuantityService;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryExpiryTransaction {
    private final VariantQuantityService quantityService;

    public InventoryExpiryTransaction(VariantQuantityService quantityService) {
        this.quantityService = quantityService;
    }

    @Transactional
    public void expire(UUID orderId, OffsetDateTime now) {
        var order = quantityService.lockOrder(orderId);
        if (order.isEmpty() || !"SEPAY_VIETQR".equals(order.get().paymentMethod())
                || !"PENDING_PAYMENT".equals(order.get().orderStatus())) {
            return;
        }
        quantityService.restoreCancelledOrder(orderId);
        if (!quantityService.cancelExpiredOrder(orderId, now)) {
            throw new IllegalStateException("Expired order changed concurrently");
        }
    }
}
