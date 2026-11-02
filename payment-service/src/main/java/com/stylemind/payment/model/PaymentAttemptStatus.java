package com.stylemind.payment.model;

public enum PaymentAttemptStatus {
    PENDING,
    SETTLED,
    EXPIRED,
    CANCELLED,
    REQUIRES_RECONCILIATION
}
