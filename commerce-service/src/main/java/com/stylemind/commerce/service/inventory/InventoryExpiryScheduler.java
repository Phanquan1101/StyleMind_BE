package com.stylemind.commerce.service.inventory;

import com.stylemind.commerce.service.order.VariantQuantityService;
import java.time.OffsetDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "stylemind.commerce.inventory.expiry-enabled",
        havingValue = "true", matchIfMissing = true)
public class InventoryExpiryScheduler {
    private final VariantQuantityService quantityService;
    private final InventoryExpiryTransaction transactions;
    private final com.stylemind.commerce.service.order.QrCheckoutReservationService qrCheckouts;

    public InventoryExpiryScheduler(VariantQuantityService quantityService, InventoryExpiryTransaction transactions,
            com.stylemind.commerce.service.order.QrCheckoutReservationService qrCheckouts) {
        this.quantityService = quantityService;
        this.transactions = transactions;
        this.qrCheckouts = qrCheckouts;
    }

    @Scheduled(fixedDelayString = "${stylemind.commerce.inventory.expiry-scan-ms:60000}")
    public void releaseExpiredReservations() {
        OffsetDateTime now = OffsetDateTime.now();
        for (var orderId : quantityService.expiredPendingSepayOrderIds(now)) {
            transactions.expire(orderId, now);
        }
        for (var checkoutId : qrCheckouts.expiredCheckoutIds(now)) {
            qrCheckouts.expire(checkoutId, now);
        }
    }
}
