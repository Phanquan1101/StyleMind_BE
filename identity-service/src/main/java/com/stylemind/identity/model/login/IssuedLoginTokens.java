package com.stylemind.identity.model.login;

import java.time.OffsetDateTime;

public record IssuedLoginTokens(
        String accessToken,
        String refreshToken,
        String refreshTokenHash,
        OffsetDateTime issuedAt,
        OffsetDateTime accessExpiresAt,
        OffsetDateTime refreshExpiresAt,
        long accessExpiresInSeconds) {

    @Override
    public String toString() {
        return "IssuedLoginTokens[accessToken=[REDACTED], refreshToken=[REDACTED], refreshTokenHash=[REDACTED],"
                + " issuedAt=" + issuedAt + ", accessExpiresAt=" + accessExpiresAt
                + ", refreshExpiresAt=" + refreshExpiresAt + ", accessExpiresInSeconds="
                + accessExpiresInSeconds + "]";
    }
}
