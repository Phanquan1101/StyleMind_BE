package com.stylemind.wardrobecommunity.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ChatSessionListResponse(
        List<ChatSessionResponse> items,
        int page,
        int size,
        @JsonProperty("total_elements") long totalElements,
        @JsonProperty("total_pages") long totalPages) {
}

