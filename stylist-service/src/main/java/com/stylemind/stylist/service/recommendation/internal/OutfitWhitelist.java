package com.stylemind.stylist.service.recommendation.internal;

import com.stylemind.stylist.model.recommendation.EngineModel.Outfit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Frozen complete-outfit aliases; a future model may select, never recompose them. */
public final class OutfitWhitelist {
    public Map<String, Outfit> freeze(List<Outfit> outfits) {
        if (outfits.size() > OutfitEngine.MAX_RESULTS) throw new IllegalArgumentException("Too many outfits");
        Map<String, Outfit> result = new LinkedHashMap<>();
        for (int i = 0; i < outfits.size(); i++) result.put("O" + (i + 1), outfits.get(i));
        return Collections.unmodifiableMap(result);
    }

    public List<Outfit> select(Map<String, Outfit> whitelist, List<String> aliases) {
        if (aliases.size() > OutfitEngine.MAX_RESULTS || aliases.stream().distinct().count() != aliases.size()
                || aliases.stream().anyMatch(a -> a == null || !whitelist.containsKey(a)))
            throw new IllegalArgumentException("Invalid or duplicate outfit alias");
        return aliases.stream().map(whitelist::get).toList();
    }
}
