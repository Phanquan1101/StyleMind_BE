package com.stylemind.payment.model;

import java.util.UUID;
import java.time.Instant;

public record PaymentAttemptSnapshot(
        UUID paymentAttemptId,
        UUID orderId,
        UUID customerId,
        String paymentReference,
        String requestKey,
        String paymentMethod,
        long expectedAmountVnd,
        String attemptStatus,
        Instant generatedAt,
        Instant expiresAt,
        String qrPayload,
        String transferContent) {
}
