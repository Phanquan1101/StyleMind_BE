package com.stylemind.commerce.dto.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OrderListResponse(
        List<OrderResponse> items,
        int page,
        int size,
        @JsonProperty("total_elements") long totalElements,
        @JsonProperty("total_pages") long totalPages) {
}
