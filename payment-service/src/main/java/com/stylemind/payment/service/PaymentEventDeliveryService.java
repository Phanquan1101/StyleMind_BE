package com.stylemind.payment.service;

import com.stylemind.payment.model.SepayWebhookExceptionReason;
import com.stylemind.payment.repository.PaymentOutboxRepository;
import com.stylemind.payment.repository.PaymentOutboxRepository.PendingEvent;
import com.stylemind.payment.repository.PaymentReconciliationRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentEventDeliveryService {
    private final PaymentOutboxRepository outbox;
    private final PaymentReconciliationRepository reconciliation;
    public PaymentEventDeliveryService(PaymentOutboxRepository outbox, PaymentReconciliationRepository reconciliation) {
        this.outbox = outbox; this.reconciliation = reconciliation;
    }
    @Transactional
    public void acknowledge(PendingEvent event, String outcome, Instant now) {
        // Publication and a rejected-Order exception commit together. Concurrent acknowledgements are harmless.
        if (outbox.acknowledge(event.id(), now) && "RECONCILIATION_REQUIRED".equals(outcome))
            reconciliation.insertPaymentException(event.attemptId(), "COMMERCE", event.id().toString(), event.reference(),
                    event.amount(), SepayWebhookExceptionReason.ORDER_PAYMENT_REJECTED, now);
    }
}
