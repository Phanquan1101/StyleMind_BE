package com.stylemind.stylist.dto.recommendation;

import com.stylemind.stylist.client.commerce.CommerceCatalogClient.CatalogImage;
import java.util.List;
import java.util.UUID;

/** Internal use-case result only; it is not registered as an HTTP response contract. */
public record InternalRecommendationResult(
        UUID requestId,
        List<RecommendedOutfit> outfits,
        List<String> warnings,
        int candidatesUsed,
        int combinationsChecked) {
    public InternalRecommendationResult {
        outfits = List.copyOf(outfits);
        warnings = List.copyOf(warnings);
    }

    public record RecommendedOutfit(long totalPriceVnd, List<RecommendedItem> items) {
        public RecommendedOutfit { items = List.copyOf(items); }
    }

    public record RecommendedItem(
            UUID productId,
            UUID variantId,
            UUID categoryId,
            UUID brandId,
            String slot,
            String productName,
            String categoryName,
            String brandName,
            String color,
            String size,
            String material,
            long unitPriceVnd,
            List<CatalogImage> images) {
        public RecommendedItem { images = List.copyOf(images); }
    }
}
