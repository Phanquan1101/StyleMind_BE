package com.stylemind.payment.service;

import com.stylemind.payment.model.PaymentAttemptSnapshot;
import java.time.Instant;

public interface PaymentOutboxService {
    void recordPaymentConfirmed(PaymentAttemptSnapshot attempt, long amountVnd, Instant confirmedAt);
}
