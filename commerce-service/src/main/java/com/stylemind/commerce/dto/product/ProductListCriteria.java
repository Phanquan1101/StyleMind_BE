package com.stylemind.commerce.dto.product;

import java.util.List;
import java.util.UUID;

public record ProductListCriteria(
        String keyword,
        List<UUID> categoryIds,
        Long minPriceVnd,
        Long maxPriceVnd,
        String demographicSegment,
        String status,
        int page,
        int size) {

    public long offset() {
        return (long) page * size;
    }
}
