package com.stylemind.identity.dto.session;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.identity.validation.auth.RefreshTokenFormatConstraint;
import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank @RefreshTokenFormatConstraint @JsonProperty("refresh_token") String refreshToken) {

    @JsonAnySetter
    public void rejectUnexpectedField(String name, JsonNode value) {
        throw new IllegalArgumentException("Unexpected refresh-token request field");
    }

    @Override
    public String toString() {
        return "RefreshTokenRequest[refreshToken=[REDACTED]]";
    }
}
