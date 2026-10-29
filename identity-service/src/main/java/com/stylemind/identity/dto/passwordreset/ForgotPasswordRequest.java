package com.stylemind.identity.dto.passwordreset;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Locale;

public record ForgotPasswordRequest(@JsonProperty("email") @NotBlank @Email String email) {

    public ForgotPasswordRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "ForgotPasswordRequest[email=[REDACTED]]";
    }
}
