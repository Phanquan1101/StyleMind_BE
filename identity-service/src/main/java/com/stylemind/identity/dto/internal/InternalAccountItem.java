package com.stylemind.identity.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record InternalAccountItem(
        @JsonProperty("account_id") UUID accountId,
        String email,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("account_status") String accountStatus) {
}
