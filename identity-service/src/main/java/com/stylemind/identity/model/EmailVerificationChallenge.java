package com.stylemind.identity.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EmailVerificationChallenge(
        UUID challengeId,
        UUID accountId,
        byte[] codeDigest,
        OffsetDateTime expiresAt,
        int failedAttempts,
        OffsetDateTime consumedAt,
        OffsetDateTime lastSendAttemptAt,
        OffsetDateTime sendWindowStartedAt,
        int sendCount,
        OffsetDateTime lockedUntil) {

    @Override
    public String toString() {
        return "EmailVerificationChallenge[challengeId=" + challengeId + ", accountId=" + accountId
                + ", codeDigest=[REDACTED], expiresAt=" + expiresAt + ", failedAttempts=" + failedAttempts
                + ", consumedAt=" + consumedAt + ", lastSendAttemptAt=" + lastSendAttemptAt
                + ", sendWindowStartedAt=" + sendWindowStartedAt + ", sendCount=" + sendCount
                + ", lockedUntil=" + lockedUntil + "]";
    }
}
