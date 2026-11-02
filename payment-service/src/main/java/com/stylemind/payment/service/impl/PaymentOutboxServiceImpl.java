package com.stylemind.payment.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.stylemind.payment.mapper.PaymentMapper;
import com.stylemind.payment.model.PaymentAttemptSnapshot;
import com.stylemind.payment.repository.PaymentOutboxRepository;
import com.stylemind.payment.service.PaymentOutboxService;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class PaymentOutboxServiceImpl implements PaymentOutboxService {

    private final PaymentMapper mapper;
    private final PaymentOutboxRepository repository;

    public PaymentOutboxServiceImpl(PaymentMapper mapper, PaymentOutboxRepository repository) {
        this.mapper = mapper;
        this.repository = repository;
    }

    @Override
    public void recordPaymentConfirmed(PaymentAttemptSnapshot attempt, long amountVnd, Instant confirmedAt) {
        try {
            String payload = mapper.toPaymentConfirmedPayload(attempt, amountVnd, confirmedAt);
            if (!repository.insertPaymentConfirmed(attempt.paymentAttemptId(), payload, confirmedAt))
                throw new IllegalStateException("Payment confirmation was not saved to outbox");
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize PaymentConfirmed outbox event", exception);
        }
    }
}
