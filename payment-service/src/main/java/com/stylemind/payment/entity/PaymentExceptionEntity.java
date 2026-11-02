package com.stylemind.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_exception", schema = "public")
public class PaymentExceptionEntity {

    protected PaymentExceptionEntity() { }

    @Id
    @Column(name = "exception_id")
    private UUID exceptionId;

    @Column(name = "payment_attempt_id")
    private UUID paymentAttemptId;

    @Column(name = "provider_name", columnDefinition = "text")
    private String providerName;

    @Column(name = "provider_event_id", columnDefinition = "text")
    private String providerEventId;

    @Column(name = "received_reference", columnDefinition = "text")
    private String receivedReference;

    @Column(name = "received_amount_vnd")
    private long receivedAmountVnd;

    @Column(name = "exception_reason", columnDefinition = "text")
    private String exceptionReason;

    @Column(name = "reconciliation_status", columnDefinition = "text")
    private String reconciliationStatus;

    @Column(name = "reviewed_by_user_id")
    private UUID reviewedByUserId;

    @Column(name = "detected_at")
    private Instant detectedAt;

    public com.stylemind.payment.model.PaymentExceptionView view() {
        return new com.stylemind.payment.model.PaymentExceptionView(exceptionId, paymentAttemptId, providerName,
                providerEventId, receivedReference, receivedAmountVnd, exceptionReason, reconciliationStatus,
                reviewedByUserId, detectedAt);
    }
}
