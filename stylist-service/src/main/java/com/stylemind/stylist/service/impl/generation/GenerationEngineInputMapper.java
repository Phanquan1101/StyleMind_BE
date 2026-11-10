package com.stylemind.stylist.service.impl.generation;

import com.stylemind.stylist.client.commerce.CommerceCatalogClient.HydratedVariant;
import com.stylemind.stylist.model.recommendation.EngineModel;
import com.stylemind.stylist.model.recommendation.EngineModel.Holding;
import com.stylemind.stylist.model.recommendation.EngineModel.Item;
import com.stylemind.stylist.model.recommendation.EngineModel.ShopVariantReference;
import com.stylemind.stylist.model.recommendation.EngineModel.Slot;
import com.stylemind.stylist.service.catalog.ReviewedCatalogPolicy.Policy;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Converts already validated Commerce variants into the P02 engine input shape. */
final class GenerationEngineInputMapper {
    private GenerationEngineInputMapper() { }

    static Item item(HydratedVariant variant, String slot, Policy policy) {
        String color = policy.color(variant.color()).orElse(null);
        return new Item(variant.productId(), variant.categoryId(), variant.brandId(), Slot.valueOf(slot),
                color, Set.of(), variant.unitPriceVnd(), true, true, Holding.NONE,
                new ShopVariantReference(variant.variantId()), false);
    }

    static List<String> candidateAttributes(Collection<HydratedVariant> candidates) {
        return candidates.stream().flatMap(item -> Stream.of(item.productName(), item.categoryName(),
                        item.color(), item.size(), item.material()))
                .filter(value -> value != null && !value.isBlank()).distinct().limit(80).toList();
    }
}
