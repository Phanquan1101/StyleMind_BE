package com.stylemind.commerce.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "qr_checkout", schema = "public")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QrCheckout {
    @Id
    @Column(name = "checkout_id")
    private UUID checkoutId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "checkout_status", nullable = false)
    private String checkoutStatus;

    @Column(name = "total_vnd", nullable = false)
    private long totalVnd;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;
}
