package com.stylemind.identity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stylemind.identity.validation.PasswordPolicy;
import com.stylemind.identity.validation.PasswordResetTokenFormatConstraint;
import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @JsonProperty("reset_token") @NotBlank @PasswordResetTokenFormatConstraint String resetToken,
        @JsonProperty("new_password") @NotBlank @PasswordPolicy String newPassword) {

    @Override
    public String toString() {
        return "ResetPasswordRequest[resetToken=[REDACTED], newPassword=[REDACTED]]";
    }
}
