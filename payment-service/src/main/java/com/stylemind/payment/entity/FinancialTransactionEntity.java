package com.stylemind.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "financial_transaction", schema = "public")
public class FinancialTransactionEntity {

    protected FinancialTransactionEntity() { }

    public UUID getTransactionId() { return transactionId; }
    public long getActualAmountVnd() { return actualAmountVnd; }

    @Id
    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "payment_attempt_id")
    private UUID paymentAttemptId;

    @Column(name = "actual_amount_vnd")
    private long actualAmountVnd;

    @Column(name = "provider_transaction_reference", columnDefinition = "text")
    private String providerTransactionReference;

    @Column(name = "provider_event_id", columnDefinition = "text")
    private String providerEventId;

    @Column(name = "provider_name", columnDefinition = "text")
    private String providerName;

    @Column(name = "recorded_by_user_id")
    private UUID recordedByUserId;

    @Column(name = "settled_at")
    private Instant settledAt;
}

