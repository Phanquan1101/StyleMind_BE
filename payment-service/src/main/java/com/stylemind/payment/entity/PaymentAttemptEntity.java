package com.stylemind.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_attempt", schema = "public")
public class PaymentAttemptEntity {

    protected PaymentAttemptEntity() { }

    @Id
    @Column(name = "payment_attempt_id")
    private UUID paymentAttemptId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "payment_reference", columnDefinition = "text")
    private String paymentReference;

    @Column(name = "request_key", columnDefinition = "text")
    private String requestKey;

    @Column(name = "payment_method", columnDefinition = "text")
    private String paymentMethod;

    @Column(name = "expected_amount_vnd")
    private long expectedAmountVnd;

    @Column(name = "attempt_status", columnDefinition = "text")
    private String attemptStatus;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "provider_order_reference", columnDefinition = "text")
    private String providerOrderReference;

    @Column(name = "provider_bank_account_reference", columnDefinition = "text")
    private String providerBankAccountReference;

    @Column(name = "provider_virtual_account", columnDefinition = "text")
    private String providerVirtualAccount;

    @Column(name = "qr_payload", columnDefinition = "text")
    private String qrPayload;

    @Column(name = "transfer_content", columnDefinition = "text")
    private String transferContent;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    public com.stylemind.payment.model.PaymentAttemptSnapshot snapshot() {
        return new com.stylemind.payment.model.PaymentAttemptSnapshot(paymentAttemptId, orderId, customerId,
                paymentReference, requestKey, paymentMethod, expectedAmountVnd, attemptStatus, generatedAt,
                expiresAt, qrPayload, transferContent);
    }
}
