package com.stylemind.identity.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record InternalAccountLookupRequest(
        @JsonProperty("account_ids") List<String> accountIds) {
}
