package com.stylemind.identity.service;

import java.time.Duration;

public record VerificationOutcome(Code code, long retryAfterSeconds) {

    public enum Code {
        VERIFIED, INVALID, EXPIRED, ATTEMPTS_EXCEEDED, ALREADY_VERIFIED, NOT_ALLOWED, SEND_LIMIT_EXCEEDED
    }

    public static VerificationOutcome of(Code code) {
        return new VerificationOutcome(code, 0);
    }

    public static VerificationOutcome retry(Code code, Duration duration) {
        long seconds = Math.max(1, (duration.toMillis() + 999) / 1000);
        return new VerificationOutcome(code, seconds);
    }
}
