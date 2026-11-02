package com.stylemind.identity.model.passwordreset;

public record PasswordResetVerification(Code code, String resetToken, long retryAfterSeconds) {

    public enum Code {
        VERIFIED,
        INVALID,
        ATTEMPTS_EXCEEDED
    }

    public static PasswordResetVerification verified(String resetToken) {
        return new PasswordResetVerification(Code.VERIFIED, resetToken, 0);
    }

    public static PasswordResetVerification invalid() {
        return new PasswordResetVerification(Code.INVALID, null, 0);
    }

    public static PasswordResetVerification locked(long retryAfterSeconds) {
        return new PasswordResetVerification(Code.ATTEMPTS_EXCEEDED, null, retryAfterSeconds);
    }

    @Override
    public String toString() {
        return "PasswordResetVerification[code=" + code + ", resetToken=[REDACTED], retryAfterSeconds="
                + retryAfterSeconds + "]";
    }
}
