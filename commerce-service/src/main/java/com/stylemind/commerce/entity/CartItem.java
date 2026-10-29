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

@Entity @Table(name = "cart_item", schema = "public")
@Getter @Setter @NoArgsConstructor
public class CartItem {
    @Id @Column(name = "cart_item_id") private UUID cartItemId;
    @Column(name = "cart_id", nullable = false) private UUID cartId;
    @Column(name = "variant_id", nullable = false) private UUID variantId;
    @Column(nullable = false) private int quantity;
    @Column(name = "last_seen_price_vnd", nullable = false) private long lastSeenPriceVnd;
    @Column(name = "added_at", nullable = false) private OffsetDateTime addedAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
}
