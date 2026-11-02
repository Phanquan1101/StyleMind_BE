package com.stylemind.identity.dto.passwordreset;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stylemind.identity.validation.auth.PasswordPolicy;
import com.stylemind.identity.validation.auth.PasswordResetTokenFormatConstraint;
import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @JsonProperty("reset_token") @NotBlank @PasswordResetTokenFormatConstraint String resetToken,
        @JsonProperty("new_password") @NotBlank @PasswordPolicy String newPassword) {

    @Override
    public String toString() {
        return "ResetPasswordRequest[resetToken=[REDACTED], newPassword=[REDACTED]]";
    }
}
