package com.stylemind.identity.dto.users;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

public record ManagedUserRolesResponse(
        @JsonProperty("account_id") UUID accountId,
        List<String> roles) {
}
