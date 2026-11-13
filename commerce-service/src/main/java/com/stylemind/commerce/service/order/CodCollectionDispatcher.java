package com.stylemind.commerce.service.order;

import com.stylemind.commerce.client.payment.PaymentClient;
import com.stylemind.commerce.repository.order.OrderRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class CodCollectionDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(CodCollectionDispatcher.class);
    private final OrderRepository orders;
    private final PaymentClient payment;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void delivered(CodDeliveryRequested event) {
        collect(event.orderId());
    }

    @Scheduled(fixedDelayString = "${stylemind.commerce.cod.collection-scan-ms:15000}")
    public void recoverPendingCollections() {
        UUID after = null;
        java.util.List<UUID> pending;
        do {
            pending = orders.findPendingCodCollectionOrderIds(after, PageRequest.of(0, 100));
            for (UUID orderId : pending) {
                collect(orderId);
                after = orderId;
            }
        } while (pending.size() == 100);
    }

    private void collect(UUID orderId) {
        try {
            payment.collectDeliveredCod(orderId);
        } catch (RuntimeException exception) {
            // Intent remains committed until the matching PaymentConfirmed inbox receipt is applied.
            LOG.warn("COD collection synchronization pending for order {}", orderId);
        }
    }
}
