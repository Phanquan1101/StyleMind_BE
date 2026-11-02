package com.stylemind.identity.dto.passwordreset;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Locale;

public record VerifyPasswordResetRequest(
        @JsonProperty("email") @NotBlank @Email String email,
        @JsonProperty("code") @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {

    public VerifyPasswordResetRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "VerifyPasswordResetRequest[email=[REDACTED], code=[REDACTED]]";
    }
}
