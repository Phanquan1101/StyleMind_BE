package com.stylemind.commerce.dto.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record VariantBatchLookupRequest(
        @JsonProperty("variant_ids") List<String> variantIds) {
}
