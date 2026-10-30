package com.stylemind.wardrobecommunity.dto.friendship;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record FriendInvitationListResponse(
        List<FriendInvitationSummaryResponse> items,
        int page,
        int size,
        @JsonProperty("total_elements") long totalElements,
        @JsonProperty("total_pages") long totalPages) {
}
