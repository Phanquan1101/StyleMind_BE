package com.stylemind.identity.service;

public record EmailDispatch(String recipientEmail, String verificationCode) {

    @Override
    public String toString() {
        return "EmailDispatch[recipientEmail=[REDACTED], verificationCode=[REDACTED]]";
    }
}
