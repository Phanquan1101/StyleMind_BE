package com.stylemind.payment.model;

import java.time.Instant;
import java.util.UUID;

public record PaymentAttemptCreation(
        UUID paymentAttemptId,
        UUID orderId,
        UUID customerId,
        String paymentReference,
        String requestKey,
        PaymentMethod paymentMethod,
        long expectedAmountVnd,
        Instant generatedAt,
        Instant expiresAt,
        String providerBankAccountReference,
        String qrPayload,
        String transferContent) {
}
