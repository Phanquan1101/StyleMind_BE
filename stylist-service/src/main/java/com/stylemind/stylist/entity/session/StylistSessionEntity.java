package com.stylemind.stylist.entity.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stylist_session", schema = "public")
public class StylistSessionEntity {

    @Id
    @Column(name = "stylist_session_id", nullable = false, updatable = false)
    private UUID stylistSessionId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "title", columnDefinition = "text")
    private String title;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected StylistSessionEntity() {
    }

    public StylistSessionEntity(UUID stylistSessionId, UUID customerId, String title,
                                Instant openedAt, Instant closedAt) {
        this.stylistSessionId = stylistSessionId;
        this.customerId = customerId;
        this.title = title;
        this.openedAt = openedAt;
        this.closedAt = closedAt;
    }

    public UUID getStylistSessionId() { return stylistSessionId; }
    public UUID getCustomerId() { return customerId; }
    public String getTitle() { return title; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getClosedAt() { return closedAt; }
}
