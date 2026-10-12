package com.stylemind.identity.dto.emailverification;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Locale;

public record VerifyEmailRequest(
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {

    public VerifyEmailRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "VerifyEmailRequest[email=[REDACTED], code=[REDACTED]]";
    }
}
