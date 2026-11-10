package com.stylemind.stylist.service.generation;

import com.stylemind.stylist.model.generation.GenerationModel.Claim;
import com.stylemind.stylist.model.generation.GenerationModel.PersistedRecommendation;
import com.stylemind.stylist.model.generation.GenerationModel.RequestRead;
import com.stylemind.stylist.model.generation.GenerationModel.StoredRecommendation;
import java.util.List;
import java.util.UUID;

public interface GenerationPersistenceService {
    RequestRead readOwned(UUID customerId, UUID requestId);
    StoredRecommendation readOwnedRecommendation(UUID customerId, UUID recommendationId);
    Claim claim(UUID customerId, UUID requestId);
    List<StoredRecommendation> complete(UUID customerId, UUID requestId, String status,
                                        List<PersistedRecommendation> recommendations);
    void markFailed(UUID customerId, UUID requestId);
}
