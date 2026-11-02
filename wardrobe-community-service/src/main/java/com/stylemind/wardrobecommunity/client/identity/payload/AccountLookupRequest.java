package com.stylemind.wardrobecommunity.client.identity.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

/** Private payload of Identity's internal account lookup; not an API DTO of this service. */
public record AccountLookupRequest(
        @JsonProperty("account_ids") List<UUID> accountIds) {
}
