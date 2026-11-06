package com.stylemind.identity.dto.profile;

import com.stylemind.identity.entity.StyleProfile;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({"style_profile"})
@Schema(name = "StyleProfileResponse")
public record StyleProfileResponse(@JsonProperty("style_profile") StyleProfile styleProfile) {

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonPropertyOrder({"fit_preference", "height_cm", "weight_kg", "usual_top_size", "usual_bottom_size", "shoe_size",
            "style_codes", "color_codes", "brand_ids"})
    public record StyleProfile(
            @JsonProperty("fit_preference") String fitPreference,
            @JsonProperty("height_cm") BigDecimal heightCm,
            @JsonProperty("weight_kg") BigDecimal weightKg,
            @JsonProperty("usual_top_size") String usualTopSize,
            @JsonProperty("usual_bottom_size") String usualBottomSize,
            @JsonProperty("shoe_size") String shoeSize,
            @JsonProperty("style_codes") List<String> styleCodes,
            @JsonProperty("color_codes") List<String> colorCodes,
            @JsonProperty("brand_ids") List<UUID> brandIds) {
    }
}
