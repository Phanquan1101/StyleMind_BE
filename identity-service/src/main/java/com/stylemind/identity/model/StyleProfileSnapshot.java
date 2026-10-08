package com.stylemind.identity.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record StyleProfileSnapshot(
        boolean profileExists,
        String fitPreference,
        BigDecimal heightCm,
        String usualTopSize,
        String usualBottomSize,
        String shoeSize,
        List<String> styleCodes,
        List<String> colorCodes,
        List<UUID> brandIds) {

    public StyleProfileSnapshot {
        styleCodes = List.copyOf(styleCodes);
        colorCodes = List.copyOf(colorCodes);
        brandIds = List.copyOf(brandIds);
    }

    public static StyleProfileSnapshot absent() {
        return new StyleProfileSnapshot(false, null, null, null, null, null, List.of(), List.of(), List.of());
    }
}
