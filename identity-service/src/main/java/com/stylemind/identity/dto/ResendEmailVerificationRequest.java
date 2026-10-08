package com.stylemind.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Locale;

public record ResendEmailVerificationRequest(@NotBlank @Email String email) {

    public ResendEmailVerificationRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "ResendEmailVerificationRequest[email=[REDACTED]]";
    }
}
