package com.stylemind.commerce.dto.product;

import java.util.List;

public record ProductListResponse(
        List<ProductResponse> items,
        int page,
        int size,
        long total_elements,
        long total_pages) {
}
