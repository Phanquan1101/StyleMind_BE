package com.stylemind.commerce.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "cart", schema = "public")
@Getter @Setter @NoArgsConstructor
public class Cart {
    @Id @Column(name = "cart_id") private UUID cartId;
    @Column(name = "customer_id") private UUID customerId;
    @Column(name = "guest_session_key", columnDefinition = "text") private String guestSessionKey;
    @Column(name = "cart_status", nullable = false, columnDefinition = "text") private String cartStatus;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
    @Column(name = "merged_at") private OffsetDateTime mergedAt;
}
