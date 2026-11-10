package com.stylemind.stylist.service.generation;

import com.stylemind.stylist.dto.generation.GenerateRecommendationsResponse;
import com.stylemind.stylist.dto.generation.RecommendationDetailResponse;
import com.stylemind.stylist.dto.generation.RequestRecommendationsResponse;
import java.util.UUID;

public interface StylistGenerationService {
    GenerateRecommendationsResponse generate(UUID customerId, UUID requestId);
    RequestRecommendationsResponse readRequestRecommendations(UUID customerId, UUID requestId);
    RecommendationDetailResponse readRecommendation(UUID customerId, UUID recommendationId);
}
