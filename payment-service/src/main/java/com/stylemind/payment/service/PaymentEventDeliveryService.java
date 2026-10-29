package com.stylemind.payment.service;

import com.stylemind.payment.repository.PaymentOutboxRepository.PendingEvent;
import java.time.Instant;

public interface PaymentEventDeliveryService {
    void acknowledge(PendingEvent event, String outcome, Instant now);
}
