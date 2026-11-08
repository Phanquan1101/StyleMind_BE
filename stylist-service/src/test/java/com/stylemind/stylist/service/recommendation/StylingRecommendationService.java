package com.stylemind.stylist.service.recommendation;

import com.stylemind.stylist.dto.recommendation.InternalRecommendationResult;
import java.util.UUID;

/** Internal orchestration entry point. No controller exposes this use case in this delivery. */
public interface StylingRecommendationService {
    InternalRecommendationResult recommend(UUID trustedCustomerId, UUID requestId);
}
