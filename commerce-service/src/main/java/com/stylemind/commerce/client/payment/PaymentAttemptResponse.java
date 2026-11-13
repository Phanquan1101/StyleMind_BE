package com.stylemind.commerce.client.payment;

import java.time.Instant;
import java.util.UUID;

public record PaymentAttemptResponse(UUID id, UUID orderId, String method, long amountVnd,
        String reference, String status, Instant expiresAt, String transferContent, String qrPayload, String paymentUrl) {
    public PaymentAttemptResponse(UUID id, UUID orderId, String method, long amountVnd, String reference,
            String status, Instant expiresAt, String transferContent, String qrPayload) {
        this(id, orderId, method, amountVnd, reference, status, expiresAt, transferContent, qrPayload, null);
    }
}
