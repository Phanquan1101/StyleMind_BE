package com.stylemind.stylist.service.recommendation.internal;

import com.stylemind.stylist.model.recommendation.EngineModel.Item;
import com.stylemind.stylist.model.recommendation.EngineModel.Outfit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Stable score ordering and distinct non-anchor outfit selection. */
public final class ResultDiversity {
    private static final Comparator<Outfit> ORDER = Comparator.comparing(Outfit::score).reversed()
            .thenComparing(ResultDiversity::variantKey);

    public List<Outfit> select(List<Outfit> candidates, int limit) {
        if (limit < 0) throw new IllegalArgumentException("Negative result limit");
        if (limit == 0) return List.of();
        List<Outfit> ordered = candidates.stream().sorted(ORDER).toList();
        List<Outfit> result = new java.util.ArrayList<>();
        Set<List<UUID>> seenNonAnchors = new HashSet<>();
        for (Outfit outfit : ordered) {
            List<UUID> nonAnchors = outfit.items().stream().filter(item -> !item.anchor())
                    .map(Item::variantId).sorted(Comparator.comparing(UUID::toString)).toList();
            if (seenNonAnchors.add(nonAnchors)) result.add(outfit);
            if (result.size() == limit) break;
        }
        return List.copyOf(result);
    }

    private static String variantKey(Outfit outfit) {
        return outfit.items().stream().map(Item::variantId).map(UUID::toString).sorted()
                .collect(Collectors.joining("/"));
    }
}
