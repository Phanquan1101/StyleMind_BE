package com.stylemind.wardrobecommunity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "outfit_item", schema = "public")
@Getter @Setter @NoArgsConstructor
public class OutfitItem {
    @Id @Column(name = "outfit_item_id") private UUID outfitItemId;
    @Column(name = "outfit_id", nullable = false) private UUID outfitId;
    @Column(name = "variant_id", nullable = false) private UUID variantId;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
}
