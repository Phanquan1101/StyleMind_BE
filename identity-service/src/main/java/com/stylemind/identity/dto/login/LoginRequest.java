package com.stylemind.identity.dto.login;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.identity.validation.common.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Locale;

public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank @MaxUtf8Bytes(72) String password) {

    public LoginRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @JsonProperty("email")
    public String email() {
        return email;
    }

    @JsonAnySetter
    public void rejectUnexpectedField(String name, JsonNode value) {
        throw new IllegalArgumentException("Unexpected login request field");
    }

    @Override
    public String toString() {
        return "LoginRequest[email=[REDACTED], password=[REDACTED]]";
    }
}
