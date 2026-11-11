package com.stylemind.payment.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refund_result_outbox", schema = "public")
public class RefundResultOutboxEntity {

    @Id
    private UUID refundId;
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "refund_id")
    private RefundCaseEntity refund;
    private Instant nextAttemptAt;
    private Instant publishedAt;

    protected RefundResultOutboxEntity() { }

    public RefundResultOutboxEntity(RefundCaseEntity refund, Instant now) {
        this.refund = refund;
        this.nextAttemptAt = now;
    }
}
