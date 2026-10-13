package com.stylemind.commerce.model.product;

import java.util.UUID;

public record ProductListCriteria(
        String keyword,
        UUID categoryId,
        Long minPriceVnd,
        Long maxPriceVnd,
        String demographicSegment,
        int page,
        int size) {

    public long offset() {
        return (long) page * size;
    }
}
