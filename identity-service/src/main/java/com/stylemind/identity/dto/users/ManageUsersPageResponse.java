package com.stylemind.identity.dto.users;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ManageUsersPageResponse(
        List<ManagedUserResponse> items,
        int page,
        int size,
        @JsonProperty("total_elements") long totalElements,
        @JsonProperty("total_pages") long totalPages) {
}
