package com.stylemind.commerce.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record InternalVariantLookupRequest(
        @JsonProperty("variant_ids") List<String> variantIds) {
}
