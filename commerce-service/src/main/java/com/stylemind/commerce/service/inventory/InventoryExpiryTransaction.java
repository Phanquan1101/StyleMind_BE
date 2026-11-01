package com.stylemind.commerce.service.inventory;

import com.stylemind.commerce.repository.inventory.InventoryRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryExpiryTransaction {
    private final InventoryRepository inventory;

    public InventoryExpiryTransaction(InventoryRepository inventory) {
        this.inventory = inventory;
    }

    @Transactional
    public void expire(UUID orderId, OffsetDateTime now) {
        var order = inventory.lockOrder(orderId);
        if (order.isEmpty() || !"SEPAY_VIETQR".equals(order.get().paymentMethod())
                || !"PENDING_PAYMENT".equals(order.get().orderStatus())) {
            return;
        }
        inventory.releaseForCancellation(orderId, now);
        if (!inventory.cancelExpiredOrder(orderId, now)) {
            throw new IllegalStateException("Expired order changed concurrently");
        }
    }
}
