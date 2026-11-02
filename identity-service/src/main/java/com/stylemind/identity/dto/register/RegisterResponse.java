package com.stylemind.identity.dto.register;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.UUID;

@JsonPropertyOrder({"account_id", "email", "account_status"})
public record RegisterResponse(
        @JsonProperty("account_id") UUID accountId,
        @JsonProperty("email") String email,
        @JsonProperty("account_status") String accountStatus) {
}
