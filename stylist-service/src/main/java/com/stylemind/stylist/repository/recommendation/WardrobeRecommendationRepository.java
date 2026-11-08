package com.stylemind.stylist.repository.recommendation;

import com.stylemind.stylist.entity.recommendation.WardrobeRecommendationEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WardrobeRecommendationRepository extends JpaRepository<WardrobeRecommendationEntity, UUID> {

    List<WardrobeRecommendationEntity> findAllByStylingRequestIdOrderByGeneratedAtAscRecommendationIdAsc(
            UUID stylingRequestId);

    @Query(value = """
            SELECT rec.*
            FROM public.wardrobe_recommendation rec
            JOIN public.styling_request req ON req.styling_request_id = rec.styling_request_id
            JOIN public.stylist_session ses ON ses.stylist_session_id = req.stylist_session_id
            WHERE rec.recommendation_id = :recommendationId AND ses.customer_id = :customerId
            """, nativeQuery = true)
    Optional<WardrobeRecommendationEntity> findByRecommendationIdAndOwner(
            @Param("recommendationId") UUID recommendationId, @Param("customerId") UUID customerId);
}
