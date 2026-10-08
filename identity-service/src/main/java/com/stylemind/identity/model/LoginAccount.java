package com.stylemind.identity.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LoginAccount(
        UUID accountId,
        String passwordHash,
        String accountStatus,
        OffsetDateTime emailVerifiedAt) {

    @Override
    public String toString() {
        return "LoginAccount[accountId=" + accountId + ", passwordHash=[REDACTED], accountStatus="
                + accountStatus + ", emailVerifiedAt=" + emailVerifiedAt + "]";
    }
}
