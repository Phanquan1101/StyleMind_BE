package com.stylemind.stylist.entity.recommendation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "recommendation_item", schema = "public")
public class RecommendationItemEntity {
    @Id
    @Column(name = "recommendation_item_id", nullable = false, updatable = false)
    private UUID recommendationItemId;

    @Column(name = "recommendation_id", nullable = false, updatable = false)
    private UUID recommendationId;

    @Column(name = "source_type", nullable = false, columnDefinition = "text", updatable = false)
    private String sourceType;

    @Column(name = "wardrobe_item_id", updatable = false)
    private UUID wardrobeItemId;

    @Column(name = "shop_variant_id", updatable = false)
    private UUID shopVariantId;

    @Column(name = "slot_name", nullable = false, columnDefinition = "text", updatable = false)
    private String slotName;

    @Column(name = "selection_reason", nullable = false, columnDefinition = "text", updatable = false)
    private String selectionReason;

    @Column(name = "display_order", nullable = false, updatable = false)
    private int displayOrder;

    protected RecommendationItemEntity() { }

    public RecommendationItemEntity(UUID recommendationItemId, UUID recommendationId, String sourceType,
                                   UUID wardrobeItemId, UUID shopVariantId, String slotName,
                                   String selectionReason, int displayOrder) {
        this.recommendationItemId = recommendationItemId;
        this.recommendationId = recommendationId;
        this.sourceType = sourceType;
        this.wardrobeItemId = wardrobeItemId;
        this.shopVariantId = shopVariantId;
        this.slotName = slotName;
        this.selectionReason = selectionReason;
        this.displayOrder = displayOrder;
    }

    public UUID getRecommendationItemId() { return recommendationItemId; }
    public UUID getRecommendationId() { return recommendationId; }
    public String getSourceType() { return sourceType; }
    public UUID getWardrobeItemId() { return wardrobeItemId; }
    public UUID getShopVariantId() { return shopVariantId; }
    public String getSlotName() { return slotName; }
    public String getSelectionReason() { return selectionReason; }
    public int getDisplayOrder() { return displayOrder; }
}
