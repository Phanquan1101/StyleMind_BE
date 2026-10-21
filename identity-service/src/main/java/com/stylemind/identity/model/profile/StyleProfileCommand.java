package com.stylemind.identity.model.profile;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record StyleProfileCommand(
        String fitPreference,
        BigDecimal heightCm,
        BigDecimal weightKg,
        String usualTopSize,
        String usualBottomSize,
        String shoeSize,
        List<String> styleCodes,
        List<String> colorCodes,
        List<UUID> brandIds) {

    public StyleProfileCommand {
        styleCodes = List.copyOf(styleCodes);
        colorCodes = List.copyOf(colorCodes);
        brandIds = List.copyOf(brandIds);
    }
}
