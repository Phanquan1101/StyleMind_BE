package com.stylemind.wardrobecommunity.client.identity.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountEmailVerificationItem(
        @JsonProperty("account_id") UUID accountId,
        String email,
        @JsonProperty("account_status") String accountStatus,
        @JsonProperty("email_verified_at") OffsetDateTime emailVerifiedAt) {
    public boolean activeAndEmailVerified() {
        return "ACTIVE".equals(accountStatus) && emailVerifiedAt != null;
    }
    @Override public String toString() {
        return "AccountEmailVerificationItem[accountId=" + accountId + ", email=[REDACTED]]";
    }
}
