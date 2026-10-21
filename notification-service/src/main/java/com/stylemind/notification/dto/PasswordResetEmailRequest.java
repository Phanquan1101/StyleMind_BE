package com.stylemind.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordResetEmailRequest(
        @NotBlank @Email String recipientEmail,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String resetCode) {

    @Override
    public String toString() {
        return "PasswordResetEmailRequest[recipientEmail=[REDACTED], resetCode=[REDACTED]]";
    }
}
