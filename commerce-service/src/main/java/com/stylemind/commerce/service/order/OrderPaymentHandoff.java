package com.stylemind.commerce.service.order;

import com.stylemind.commerce.client.payment.OrderPaymentRequest;
import com.stylemind.commerce.client.payment.PaymentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class OrderPaymentHandoff {
    private static final Logger log = LoggerFactory.getLogger(OrderPaymentHandoff.class);
    private final PaymentClient client;
    private final CodOrderConfirmationService codConfirmation;

    public OrderPaymentHandoff(PaymentClient client, CodOrderConfirmationService codConfirmation) {
        this.client = client;
        this.codConfirmation = codConfirmation;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void afterOrderCommitted(OrderPaymentRequest order) {
        try {
            var attempt = client.create(order);
            if ("COD".equals(order.paymentMethod())) codConfirmation.confirm(order, attempt);
        } catch (RuntimeException exception) {
            // Never log an HTTP exception/body/header; payment failure must not fail a committed checkout.
            log.warn("Initial payment handoff was not confirmed; explicit order payment recovery is available");
        }
    }
}
