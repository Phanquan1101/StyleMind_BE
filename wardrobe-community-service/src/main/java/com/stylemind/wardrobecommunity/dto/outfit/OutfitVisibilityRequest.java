package com.stylemind.wardrobecommunity.dto.outfit;

import io.swagger.v3.oas.annotations.media.Schema;

/** OpenAPI shape; the controller validates the raw JSON to reject duplicate/trailing fields. */
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record OutfitVisibilityRequest(
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        allowableValues = {"PRIVATE", "FRIENDS", "PUBLIC"})
                String visibility) {}
