package com.stylemind.identity.model.internal;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Account-owned facts only; neither password data nor a UserProfile join is needed. */
public record AccountEmailVerificationSnapshot(
        UUID accountId, String email, String accountStatus, OffsetDateTime emailVerifiedAt) {
    @Override public String toString() {
        return "AccountEmailVerificationSnapshot[accountId=" + accountId + ", email=[REDACTED]]";
    }
}
