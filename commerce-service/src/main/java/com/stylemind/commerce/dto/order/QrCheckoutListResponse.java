package com.stylemind.commerce.dto.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record QrCheckoutListResponse(
        List<Item> items,
        int page,
        int size,
        @JsonProperty("total_elements") long totalElements,
        @JsonProperty("total_pages") int totalPages) {
    public record Item(UUID checkoutId, String status, long totalVnd, OffsetDateTime createdAt,
                       OffsetDateTime expiresAt) { }
}
