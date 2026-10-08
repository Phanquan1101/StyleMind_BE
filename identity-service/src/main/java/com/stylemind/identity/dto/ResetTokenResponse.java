package com.stylemind.identity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ResetTokenResponse(
        @JsonProperty("reset_token") String resetToken,
        @JsonProperty("expires_in") long expiresIn) {

    @Override
    public String toString() {
        return "ResetTokenResponse[resetToken=[REDACTED], expiresIn=" + expiresIn + "]";
    }
}
