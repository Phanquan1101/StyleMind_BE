package com.stylemind.identity.model.passwordreset;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PasswordResetChallenge(
        UUID challengeId,
        UUID accountId,
        byte[] otpDigest,
        OffsetDateTime otpExpiresAt,
        int failedAttempts,
        OffsetDateTime lockedUntil,
        OffsetDateTime lastSendAttemptAt,
        OffsetDateTime sendWindowStartedAt,
        int sendCount,
        OffsetDateTime verifiedAt,
        byte[] resetTokenHash,
        OffsetDateTime resetTokenExpiresAt,
        OffsetDateTime consumedAt) {

    @Override
    public String toString() {
        return "PasswordResetChallenge[challengeId=" + challengeId + ", accountId=" + accountId
                + ", otpDigest=[REDACTED], otpExpiresAt=" + otpExpiresAt + ", failedAttempts=" + failedAttempts
                + ", lockedUntil=" + lockedUntil + ", lastSendAttemptAt=" + lastSendAttemptAt
                + ", sendWindowStartedAt=" + sendWindowStartedAt + ", sendCount=" + sendCount
                + ", verifiedAt=" + verifiedAt + ", resetTokenHash=[REDACTED], resetTokenExpiresAt="
                + resetTokenExpiresAt + ", consumedAt=" + consumedAt + "]";
    }
}
