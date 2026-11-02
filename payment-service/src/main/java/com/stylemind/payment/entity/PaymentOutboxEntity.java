package com.stylemind.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_outbox", schema = "public")
public class PaymentOutboxEntity {

    protected PaymentOutboxEntity() { }

    @Id
    @Column(name = "outbox_event_id")
    private UUID outboxEventId;

    @Column(name = "aggregate_type", columnDefinition = "text")
    private String aggregateType;

    @Column(name = "aggregate_id")
    private UUID aggregateId;

    @Column(name = "event_type", columnDefinition = "text")
    private String eventType;

    @Column(name = "event_payload", columnDefinition = "text")
    private String eventPayload;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;
}
