package com.stylemind.identity.dto;

import com.stylemind.identity.validation.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Locale;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @PasswordPolicy String password) {

    public RegisterRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "RegisterRequest[email=[REDACTED], password=[REDACTED]]";
    }
}
