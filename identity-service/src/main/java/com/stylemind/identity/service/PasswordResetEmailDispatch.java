package com.stylemind.identity.service;

public record PasswordResetEmailDispatch(String recipientEmail, String verificationCode) {

    @Override
    public String toString() {
        return "PasswordResetEmailDispatch[recipientEmail=[REDACTED], verificationCode=[REDACTED]]";
    }
}
