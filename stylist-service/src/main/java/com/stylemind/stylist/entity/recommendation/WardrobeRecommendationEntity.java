package com.stylemind.stylist.entity.recommendation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wardrobe_recommendation", schema = "public")
public class WardrobeRecommendationEntity {
    @Id
    @Column(name = "recommendation_id", nullable = false, updatable = false)
    private UUID recommendationId;

    @Column(name = "styling_request_id", nullable = false, updatable = false)
    private UUID stylingRequestId;

    @Column(name = "explanation", nullable = false, columnDefinition = "text", updatable = false)
    private String explanation;

    @Column(name = "styling_tips", columnDefinition = "text", updatable = false)
    private String stylingTips;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    protected WardrobeRecommendationEntity() { }

    public WardrobeRecommendationEntity(UUID recommendationId, UUID stylingRequestId, String explanation,
                                        String stylingTips, Instant generatedAt) {
        this.recommendationId = recommendationId;
        this.stylingRequestId = stylingRequestId;
        this.explanation = explanation;
        this.stylingTips = stylingTips;
        this.generatedAt = generatedAt;
    }

    public UUID getRecommendationId() { return recommendationId; }
    public UUID getStylingRequestId() { return stylingRequestId; }
    public String getExplanation() { return explanation; }
    public String getStylingTips() { return stylingTips; }
    public Instant getGeneratedAt() { return generatedAt; }
}
