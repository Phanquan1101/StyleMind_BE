package com.stylemind.identity.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RefreshSession(
        UUID sessionId,
        UUID accountId,
        String tokenHash,
        OffsetDateTime issuedAt,
        OffsetDateTime expiresAt,
        OffsetDateTime revokedAt) {

    @Override
    public String toString() {
        return "RefreshSession[sessionId=" + sessionId + ", accountId=" + accountId
                + ", tokenHash=[REDACTED], issuedAt=" + issuedAt + ", expiresAt=" + expiresAt
                + ", revokedAt=" + revokedAt + "]";
    }
}
