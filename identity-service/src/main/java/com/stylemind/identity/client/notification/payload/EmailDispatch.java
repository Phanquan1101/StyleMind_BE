package com.stylemind.identity.client.notification.payload;

public record EmailDispatch(String recipientEmail, String verificationCode) {

    @Override
    public String toString() {
        return "EmailDispatch[recipientEmail=[REDACTED], verificationCode=[REDACTED]]";
    }
}
