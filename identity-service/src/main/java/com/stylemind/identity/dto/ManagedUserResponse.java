package com.stylemind.identity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ManagedUserResponse(
        @JsonProperty("account_id") UUID accountId,
        String email,
        @JsonProperty("account_status") String accountStatus,
        @JsonProperty("email_verified_at") OffsetDateTime emailVerifiedAt,
        @JsonProperty("registered_at") OffsetDateTime registeredAt,
        List<String> roles) {
}
