package com.stylemind.commerce.service.inventory;

import com.stylemind.commerce.repository.inventory.InventoryRepository;
import java.time.OffsetDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "stylemind.commerce.inventory.expiry-enabled",
        havingValue = "true", matchIfMissing = true)
public class InventoryExpiryScheduler {
    private final InventoryRepository inventory;
    private final InventoryExpiryTransaction transactions;

    public InventoryExpiryScheduler(InventoryRepository inventory, InventoryExpiryTransaction transactions) {
        this.inventory = inventory;
        this.transactions = transactions;
    }

    @Scheduled(fixedDelayString = "${stylemind.commerce.inventory.expiry-scan-ms:60000}")
    public void releaseExpiredReservations() {
        OffsetDateTime now = OffsetDateTime.now();
        for (var orderId : inventory.expiredOrderIds(now)) {
            transactions.expire(orderId, now);
        }
    }
}
