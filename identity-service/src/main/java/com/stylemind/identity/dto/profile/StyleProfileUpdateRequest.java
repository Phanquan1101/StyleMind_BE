package com.stylemind.identity.dto.profile;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Validated, canonical form of the public style-profile replacement request. */
public record StyleProfileUpdateRequest(
        String fitPreference,
        BigDecimal heightCm,
        BigDecimal weightKg,
        String usualTopSize,
        String usualBottomSize,
        String shoeSize,
        List<String> styleCodes,
        List<String> colorCodes,
        List<UUID> brandIds) {

    public StyleProfileUpdateRequest {
        styleCodes = List.copyOf(styleCodes);
        colorCodes = List.copyOf(colorCodes);
        brandIds = List.copyOf(brandIds);
    }
}
