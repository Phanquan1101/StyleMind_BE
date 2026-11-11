package com.stylemind.payment.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refund_audit", schema = "public")
public class RefundAuditEntity {

    public enum Action { PREPARED, DESTINATION_SAVED, STARTED, RETRIED, DESTINATION_VIEWED, REFUNDED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long auditId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "refund_id")
    private RefundCaseEntity refund;
    private UUID actorId;
    @Enumerated(EnumType.STRING)
    private Action action;
    private String reason;
    private Instant recordedAt;

    protected RefundAuditEntity() { }

    public RefundAuditEntity(RefundCaseEntity refund, UUID actorId, Action action, String reason, Instant recordedAt) {
        this.refund = refund;
        this.actorId = actorId;
        this.action = action;
        this.reason = reason;
        this.recordedAt = recordedAt;
    }
}
