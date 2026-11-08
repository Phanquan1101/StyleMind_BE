package com.stylemind.wardrobecommunity.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ChatHistoryResponse(
        List<ChatMessageResponse> items,
        @JsonProperty("has_more") boolean hasMore,
        @JsonProperty("next_before_sequence") Long nextBeforeSequence) {
    public ChatHistoryResponse { items = List.copyOf(items); }
}

