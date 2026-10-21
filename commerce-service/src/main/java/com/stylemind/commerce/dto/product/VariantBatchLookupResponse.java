package com.stylemind.commerce.dto.product;

import java.util.List;

public record VariantBatchLookupResponse(
        List<VariantResponse> items) {
}
