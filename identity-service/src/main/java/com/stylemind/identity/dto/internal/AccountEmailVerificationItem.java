package com.stylemind.identity.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountEmailVerificationItem(
        @JsonProperty("account_id") UUID accountId,
        String email,
        @JsonProperty("account_status") String accountStatus,
        @JsonProperty("email_verified_at") OffsetDateTime emailVerifiedAt) {
    @Override public String toString() {
        return "AccountEmailVerificationItem[accountId=" + accountId + ", email=[REDACTED]]";
    }
}
