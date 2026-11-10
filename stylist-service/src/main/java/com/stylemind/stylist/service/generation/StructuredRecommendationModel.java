package com.stylemind.stylist.service.generation;

import com.stylemind.stylist.model.generation.GenerationModel.FrozenOutfit;
import com.stylemind.stylist.model.generation.GenerationModel.ModelOutput;
import com.stylemind.stylist.model.generation.GenerationModel.RequestContext;
import java.time.Duration;
import java.util.List;

public interface StructuredRecommendationModel {
    ModelOutput select(RequestContext context, List<FrozenOutfit> whitelist, List<String> knowledgeFacts,
                       Duration remaining);
    boolean isConfigured();
}
