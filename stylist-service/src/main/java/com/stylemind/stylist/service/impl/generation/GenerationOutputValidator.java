package com.stylemind.stylist.service.impl.generation;

import com.stylemind.stylist.client.commerce.CommerceCatalogClient.HydratedVariant;
import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import com.stylemind.stylist.model.generation.GenerationModel.FrozenOutfit;
import com.stylemind.stylist.model.generation.GenerationModel.ItemAlias;
import com.stylemind.stylist.model.generation.GenerationModel.ModelItemReason;
import com.stylemind.stylist.model.generation.GenerationModel.ModelOutput;
import com.stylemind.stylist.model.generation.GenerationModel.ModelRecommendation;
import com.stylemind.stylist.model.recommendation.EngineModel.Item;
import com.stylemind.stylist.model.recommendation.EngineModel.Outfit;
import com.stylemind.stylist.model.recommendation.EngineModel.Slot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Owns the fixed candidate aliases and validates provider output against that whitelist. */
final class GenerationOutputValidator {
    List<FrozenOutfit> freeze(List<Outfit> outfits, Map<UUID, HydratedVariant> currentByVariant) {
        List<FrozenOutfit> frozen = new ArrayList<>();
        int index = 1;
        for (Outfit outfit : outfits) {
            String outfitAlias = "O" + index++;
            List<Item> ordered = outfit.items().stream()
                    .sorted(Comparator.comparingInt(item -> item.slot() == Slot.TOP ? 1 : 2)).toList();
            List<ItemAlias> aliases = new ArrayList<>();
            for (var item : ordered) {
                HydratedVariant product = currentByVariant.get(item.variantId());
                if (product == null) throw protocolError();
                String suffix = item.slot().name();
                aliases.add(new ItemAlias(outfitAlias + "_" + suffix, product.productId(), product.variantId(),
                        product.categoryId(), suffix, product.productName(), product.categoryName(),
                        product.color(), product.size(), product.material()));
            }
            if (aliases.size() != 2 || !"TOP".equals(aliases.getFirst().slot())
                    || !"BOTTOM".equals(aliases.get(1).slot())) throw protocolError();
            frozen.add(new FrozenOutfit(outfitAlias, outfit, aliases));
        }
        return List.copyOf(frozen);
    }

    List<ModelRecommendation> validate(ModelOutput output, List<FrozenOutfit> whitelist) {
        if (output == null || output.recommendations() == null || output.recommendations().isEmpty()
                || output.recommendations().size() > 3) throw protocolError();
        Map<String, FrozenOutfit> allowed = new HashMap<>();
        whitelist.forEach(outfit -> allowed.put(outfit.alias(), outfit));
        Set<String> seen = new HashSet<>();
        for (ModelRecommendation recommendation : output.recommendations()) {
            FrozenOutfit outfit = recommendation == null ? null : allowed.get(recommendation.outfitAlias());
            if (outfit == null || !seen.add(recommendation.outfitAlias())
                    || blankBounded(recommendation.explanation(), 2_000)
                    || recommendation.stylingTips() != null && tooLong(recommendation.stylingTips(), 1_000)
                    || recommendation.itemReasons() == null || recommendation.itemReasons().size() != 2) {
                throw protocolError();
            }
            Map<String, Boolean> expected = new HashMap<>();
            outfit.items().forEach(item -> expected.put(item.alias(), Boolean.TRUE));
            Set<String> reasonAliases = new HashSet<>();
            for (ModelItemReason reason : recommendation.itemReasons()) {
                if (reason == null || !expected.containsKey(reason.itemAlias()) || !reasonAliases.add(reason.itemAlias())
                        || blankBounded(reason.reason(), 500)) throw protocolError();
            }
            if (!expected.keySet().equals(reasonAliases)) throw protocolError();
        }
        return output.recommendations();
    }

    FrozenOutfit find(List<FrozenOutfit> outfits, String alias) {
        return outfits.stream().filter(outfit -> outfit.alias().equals(alias)).findFirst()
                .orElseThrow(GenerationOutputValidator::protocolError);
    }

    private static boolean blankBounded(String value, int max) {
        return value == null || value.isBlank() || tooLong(value, max) || value.indexOf('\0') >= 0;
    }

    private static boolean tooLong(String value, int max) {
        return value.codePointCount(0, value.length()) > max;
    }

    private static StylistApiException protocolError() {
        return new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
    }
}
