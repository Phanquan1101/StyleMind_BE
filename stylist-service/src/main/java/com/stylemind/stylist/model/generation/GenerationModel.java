package com.stylemind.stylist.model.generation;

import com.stylemind.stylist.model.recommendation.EngineModel.Outfit;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Internal generation values; never serialized as an API response or persisted as a separate model layer. */
public final class GenerationModel {
    private GenerationModel() { }

    public record Constraint(String kind, UUID catalogEntityId, String textValue, boolean hard) { }

    public record RequestContext(UUID requestId, UUID sessionId, String status, String message,
                                 String occasion, String intendedUse, String styleContext,
                                 String weatherContext, Long maxBudgetVnd, boolean hardBudget,
                                 boolean sessionClosed, List<Constraint> constraints) {
        public RequestContext { constraints = List.copyOf(constraints); }
    }

    public record Claim(String status, boolean acquired) { }

    public record RuntimeCandidate(UUID productId, UUID variantId, UUID categoryId, String categoryName,
                                   String slot, String demographicSegment, String productName,
                                   String description, String color, String size, String material,
                                   double retrievalScore) { }

    public record SearchHit(UUID pointId, double score, Map<String, Object> payload) {
        public SearchHit { payload = Map.copyOf(payload); }
    }

    public record ItemAlias(String alias, UUID productId, UUID variantId, UUID categoryId,
                            String slot, String productName, String categoryName,
                            String color, String size, String material) { }

    public record FrozenOutfit(String alias, Outfit outfit, List<ItemAlias> items) {
        public FrozenOutfit { items = List.copyOf(items); }
    }

    public record ModelItemReason(String itemAlias, String reason) { }
    public record ModelRecommendation(String outfitAlias, String explanation, String stylingTips,
                                      List<ModelItemReason> itemReasons) {
        public ModelRecommendation { itemReasons = List.copyOf(itemReasons); }
    }
    public record ModelOutput(List<ModelRecommendation> recommendations) {
        public ModelOutput { recommendations = List.copyOf(recommendations); }
    }

    public record PersistedItem(UUID shopVariantId, String slot, String reason, int displayOrder) { }
    public record PersistedRecommendation(UUID recommendationId, String explanation, String stylingTips,
                                          Instant generatedAt, List<PersistedItem> items) {
        public PersistedRecommendation { items = List.copyOf(items); }
    }
    public record StoredItem(String sourceType, UUID wardrobeItemId, UUID shopVariantId, String slot,
                             String reason, int displayOrder) { }
    public record StoredRecommendation(UUID recommendationId, UUID stylingRequestId, String explanation, String stylingTips,
                                       Instant generatedAt, List<StoredItem> items) {
        public StoredRecommendation { items = List.copyOf(items); }
    }
    public record RequestRead(RequestContext context, List<StoredRecommendation> recommendations) {
        public RequestRead { recommendations = List.copyOf(recommendations); }
    }
}
