package com.stylemind.wardrobecommunity.client.identity.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountLookupItem(
        @JsonProperty("account_id") UUID accountId,
        String email,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("account_status") String accountStatus) {

    @Override
    public String toString() {
        return "AccountLookupItem[accountId=" + accountId + ", email=[REDACTED], displayName=[REDACTED], accountStatus="
                + accountStatus + "]";
    }
}
