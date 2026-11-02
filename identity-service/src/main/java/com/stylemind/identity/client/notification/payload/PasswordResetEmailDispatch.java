package com.stylemind.identity.client.notification.payload;

public record PasswordResetEmailDispatch(String recipientEmail, String verificationCode) {

    @Override
    public String toString() {
        return "PasswordResetEmailDispatch[recipientEmail=[REDACTED], verificationCode=[REDACTED]]";
    }
}
