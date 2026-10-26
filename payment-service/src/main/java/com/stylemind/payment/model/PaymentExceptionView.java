package com.stylemind.payment.model;

import java.time.Instant;
import java.util.UUID;

public record PaymentExceptionView(
        UUID exceptionId,
        UUID paymentAttemptId,
        String providerName,
        String providerEventId,
        String receivedReference,
        long receivedAmountVnd,
        String exceptionReason,
        String reconciliationStatus,
        UUID reviewedByUserId,
        Instant detectedAt) {
}
