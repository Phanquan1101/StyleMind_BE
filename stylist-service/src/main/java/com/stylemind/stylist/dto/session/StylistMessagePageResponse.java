package com.stylemind.stylist.dto.session;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record StylistMessagePageResponse(
        List<StylistMessageResponse> items,
        @JsonProperty("next_cursor") String nextCursor) {
    public StylistMessagePageResponse {
        items = List.copyOf(items);
    }
}
