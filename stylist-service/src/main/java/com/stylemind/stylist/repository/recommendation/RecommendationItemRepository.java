package com.stylemind.stylist.repository.recommendation;

import com.stylemind.stylist.entity.recommendation.RecommendationItemEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationItemRepository extends JpaRepository<RecommendationItemEntity, UUID> {
    List<RecommendationItemEntity> findAllByRecommendationIdOrderByDisplayOrderAsc(UUID recommendationId);

    List<RecommendationItemEntity> findAllByRecommendationIdInOrderByRecommendationIdAscDisplayOrderAsc(
            Collection<UUID> recommendationIds);
}
