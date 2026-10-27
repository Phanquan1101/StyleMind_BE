package com.stylemind.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.payment.model.PaymentAttemptSnapshot;
import com.stylemind.payment.repository.PaymentOutboxRepository;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PaymentOutboxService {

    private final ObjectMapper objectMapper;
    private final PaymentOutboxRepository repository;

    public PaymentOutboxService(ObjectMapper objectMapper, PaymentOutboxRepository repository) {
        this.objectMapper = objectMapper;
        this.repository = repository;
    }

    public void recordPaymentConfirmed(PaymentAttemptSnapshot attempt, long amountVnd, Instant confirmedAt) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "payment_attempt_id", attempt.paymentAttemptId(),
                    "order_id", attempt.orderId(),
                    "customer_id", attempt.customerId(),
                    "payment_reference", attempt.paymentReference(),
                    "payment_method", attempt.paymentMethod(),
                    "amount_vnd", amountVnd,
                    "confirmed_at", confirmedAt.toString()));
            if (!repository.insertPaymentConfirmed(attempt.paymentAttemptId(), payload, confirmedAt))
                throw new IllegalStateException("Payment confirmation was not saved to outbox");
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize PaymentConfirmed outbox event", exception);
        }
    }
}
