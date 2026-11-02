package com.stylemind.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmailVerificationRequest(
        @NotBlank @Email String recipientEmail,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String verificationCode) {

    @Override
    public String toString() {
        return "EmailVerificationRequest[recipientEmail=[REDACTED], verificationCode=[REDACTED]]";
    }
}
