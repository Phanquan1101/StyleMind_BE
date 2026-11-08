package com.stylemind.stylist.mapper.recommendation;

import com.stylemind.stylist.entity.recommendation.RecommendationItemEntity;
import com.stylemind.stylist.entity.recommendation.WardrobeRecommendationEntity;
import com.stylemind.stylist.client.commerce.CommerceCatalogClient.HydratedVariant;
import com.stylemind.stylist.dto.generation.GenerateRecommendationsResponse;
import com.stylemind.stylist.dto.generation.RecommendationDetailResponse;
import com.stylemind.stylist.dto.generation.RequestRecommendationsResponse;
import com.stylemind.stylist.dto.generation.GenerateRecommendationsResponse.Recommendation;
import com.stylemind.stylist.model.generation.GenerationModel.PersistedItem;
import com.stylemind.stylist.model.generation.GenerationModel.PersistedRecommendation;
import com.stylemind.stylist.model.generation.GenerationModel.StoredItem;
import com.stylemind.stylist.model.generation.GenerationModel.StoredRecommendation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RecommendationMapper {
    public GenerateRecommendationsResponse generationResponse(UUID requestId, String status,
            List<StoredRecommendation> stored, List<String> limitations) {
        List<Recommendation> recommendations = stored.stream().map(recommendation -> new Recommendation(
                recommendation.recommendationId(), recommendation.explanation(), recommendation.stylingTips(),
                recommendation.generatedAt(), recommendation.items().stream().map(item ->
                        new GenerateRecommendationsResponse.Item(item.sourceType(), item.shopVariantId(),
                                item.slot(), item.reason(), item.displayOrder())).toList())).toList();
        return new GenerateRecommendationsResponse(requestId, status, recommendations, limitations);
    }

    public RequestRecommendationsResponse requestRecommendations(UUID requestId, String status,
            List<StoredRecommendation> stored) {
        List<RequestRecommendationsResponse.Recommendation> recommendations = stored.stream().map(recommendation ->
                new RequestRecommendationsResponse.Recommendation(recommendation.recommendationId(),
                        recommendation.explanation(), recommendation.stylingTips(), recommendation.generatedAt(),
                        recommendation.items().stream().map(item -> new RequestRecommendationsResponse.Item(
                                item.sourceType(), item.wardrobeItemId(), item.shopVariantId(), item.slot(),
                                item.reason(), item.displayOrder())).toList())).toList();
        return new RequestRecommendationsResponse(requestId, status, recommendations);
    }

    public RecommendationDetailResponse.Item unavailable(StoredItem item) {
        return new RecommendationDetailResponse.Item(item.sourceType(), item.wardrobeItemId(),
                item.shopVariantId(), item.slot(), item.reason(), item.displayOrder(), "UNAVAILABLE",
                null, null, null, null, null, null, null, null, null, null, List.of());
    }

    public RecommendationDetailResponse.Item available(StoredItem item, HydratedVariant variant) {
        return new RecommendationDetailResponse.Item(item.sourceType(), item.wardrobeItemId(), item.shopVariantId(),
                item.slot(), item.reason(), item.displayOrder(), "AVAILABLE", variant.productId(), variant.categoryId(),
                variant.brandId(), variant.productName(), variant.categoryName(), variant.brandName(), variant.color(),
                variant.size(), variant.material(), variant.unitPriceVnd(), variant.images());
    }

    public WardrobeRecommendationEntity recommendation(UUID requestId, PersistedRecommendation input) {
        return new WardrobeRecommendationEntity(input.recommendationId(), requestId, input.explanation(),
                input.stylingTips(), input.generatedAt());
    }

    public List<RecommendationItemEntity> items(UUID recommendationId, List<PersistedItem> input) {
        return input.stream().map(item -> new RecommendationItemEntity(UUID.randomUUID(), recommendationId,
                "SHOP_VARIANT", null, item.shopVariantId(), item.slot(), item.reason(), item.displayOrder())).toList();
    }

    public StoredRecommendation stored(WardrobeRecommendationEntity recommendation,
                                       List<RecommendationItemEntity> items) {
        return new StoredRecommendation(recommendation.getRecommendationId(), recommendation.getStylingRequestId(),
                recommendation.getExplanation(),
                recommendation.getStylingTips(), recommendation.getGeneratedAt(),
                items.stream().map(this::stored).toList());
    }

    private StoredItem stored(RecommendationItemEntity item) {
        return new StoredItem(item.getSourceType(), item.getWardrobeItemId(), item.getShopVariantId(),
                item.getSlotName(), item.getSelectionReason(), item.getDisplayOrder());
    }
}
