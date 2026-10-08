package com.stylemind.identity.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.stylemind.identity.validation.StrictJsonDecimalDeserializer;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ReplaceStyleProfileRequest {

    private JsonNode fitPreference;
    private BigDecimal heightCm;
    private JsonNode usualTopSize;
    private JsonNode usualBottomSize;
    private JsonNode shoeSize;
    private JsonNode styleCodes;
    private JsonNode colorCodes;
    private JsonNode brandIds;
    private final Set<String> unknownProperties = new LinkedHashSet<>();
    private boolean fitPreferencePresent;
    private boolean heightCmPresent;
    private boolean usualTopSizePresent;
    private boolean usualBottomSizePresent;
    private boolean shoeSizePresent;
    private boolean styleCodesPresent;
    private boolean colorCodesPresent;
    private boolean brandIdsPresent;

    @JsonSetter(value = "fit_preference", nulls = Nulls.SET)
    public void setFitPreference(JsonNode fitPreference) {
        this.fitPreferencePresent = true;
        this.fitPreference = fitPreference;
    }

    @JsonProperty("fit_preference")
    @Schema(type = "string", nullable = true, description = "Optional syntax-only code; stored uppercase.")
    public JsonNode getFitPreference() {
        return fitPreference;
    }

    @JsonSetter(value = "height_cm", nulls = Nulls.SET)
    @JsonDeserialize(using = StrictJsonDecimalDeserializer.class)
    public void setHeightCm(BigDecimal heightCm) {
        this.heightCmPresent = true;
        this.heightCm = heightCm;
    }

    @JsonProperty("height_cm")
    @Schema(type = "number", nullable = true,
            description = "Exact decimal centimeters in [50, 250], with at most one fractional digit.")
    public BigDecimal getHeightCm() {
        return heightCm;
    }

    @JsonSetter(value = "usual_top_size", nulls = Nulls.SET)
    public void setUsualTopSize(JsonNode usualTopSize) {
        this.usualTopSizePresent = true;
        this.usualTopSize = usualTopSize;
    }

    @JsonProperty("usual_top_size")
    @Schema(type = "string", nullable = true, description = "Free-form trimmed label, at most 32 code points.")
    public JsonNode getUsualTopSize() {
        return usualTopSize;
    }

    @JsonSetter(value = "usual_bottom_size", nulls = Nulls.SET)
    public void setUsualBottomSize(JsonNode usualBottomSize) {
        this.usualBottomSizePresent = true;
        this.usualBottomSize = usualBottomSize;
    }

    @JsonProperty("usual_bottom_size")
    @Schema(type = "string", nullable = true, description = "Free-form trimmed label, at most 32 code points.")
    public JsonNode getUsualBottomSize() {
        return usualBottomSize;
    }

    @JsonSetter(value = "shoe_size", nulls = Nulls.SET)
    public void setShoeSize(JsonNode shoeSize) {
        this.shoeSizePresent = true;
        this.shoeSize = shoeSize;
    }

    @JsonProperty("shoe_size")
    @Schema(type = "string", nullable = true, description = "Free-form trimmed label, at most 32 code points.")
    public JsonNode getShoeSize() {
        return shoeSize;
    }

    @JsonSetter(value = "style_codes", nulls = Nulls.SET)
    public void setStyleCodes(JsonNode styleCodes) {
        this.styleCodesPresent = true;
        this.styleCodes = styleCodes;
    }

    @JsonProperty("style_codes")
    @ArraySchema(schema = @Schema(type = "string"), arraySchema = @Schema(description = "Required; up to 50 unique syntax-only codes."))
    public JsonNode getStyleCodes() {
        return styleCodes;
    }

    @JsonSetter(value = "color_codes", nulls = Nulls.SET)
    public void setColorCodes(JsonNode colorCodes) {
        this.colorCodesPresent = true;
        this.colorCodes = colorCodes;
    }

    @JsonProperty("color_codes")
    @ArraySchema(schema = @Schema(type = "string"), arraySchema = @Schema(description = "Required; up to 50 unique syntax-only codes."))
    public JsonNode getColorCodes() {
        return colorCodes;
    }

    @JsonSetter(value = "brand_ids", nulls = Nulls.SET)
    public void setBrandIds(JsonNode brandIds) {
        this.brandIdsPresent = true;
        this.brandIds = brandIds;
    }

    @JsonProperty("brand_ids")
    @ArraySchema(schema = @Schema(type = "string", format = "uuid"),
            arraySchema = @Schema(description = "Required; up to 50 unique hyphenated UUIDs; Commerce existence/active status is not verified."))
    public JsonNode getBrandIds() {
        return brandIds;
    }

    @JsonAnySetter
    public void addUnknownProperty(String name, JsonNode value) {
        unknownProperties.add(name);
    }

    @JsonIgnore public boolean isFitPreferencePresent() { return fitPreferencePresent; }
    @JsonIgnore public boolean isHeightCmPresent() { return heightCmPresent; }
    @JsonIgnore public boolean isUsualTopSizePresent() { return usualTopSizePresent; }
    @JsonIgnore public boolean isUsualBottomSizePresent() { return usualBottomSizePresent; }
    @JsonIgnore public boolean isShoeSizePresent() { return shoeSizePresent; }
    @JsonIgnore public boolean isStyleCodesPresent() { return styleCodesPresent; }
    @JsonIgnore public boolean isColorCodesPresent() { return colorCodesPresent; }
    @JsonIgnore public boolean isBrandIdsPresent() { return brandIdsPresent; }

    @JsonIgnore
    public Set<String> getUnknownProperties() {
        return Set.copyOf(unknownProperties);
    }
}
