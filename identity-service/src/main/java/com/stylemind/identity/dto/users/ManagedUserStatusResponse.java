package com.stylemind.identity.dto.users;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ManagedUserStatusResponse(
        @JsonProperty("account_id") UUID accountId,
        @JsonProperty("account_status") String accountStatus,
        @JsonProperty("email_verified_at") OffsetDateTime emailVerifiedAt) {
}
