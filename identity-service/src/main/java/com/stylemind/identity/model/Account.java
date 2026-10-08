package com.stylemind.identity.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Account(
        UUID accountId,
        String email,
        String passwordHash,
        String accountStatus,
        OffsetDateTime emailVerifiedAt,
        OffsetDateTime registeredAt) {

    @Override
    public String toString() {
        return "Account[accountId=" + accountId + ", email=[REDACTED], passwordHash=[REDACTED], accountStatus="
                + accountStatus + ", emailVerifiedAt=" + emailVerifiedAt + ", registeredAt=" + registeredAt + "]";
    }
}
