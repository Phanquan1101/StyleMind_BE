package com.stylemind.identity.model.users;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminManagedAccountState(
        UUID accountId,
        String accountStatus,
        OffsetDateTime emailVerifiedAt,
        OffsetDateTime registeredAt) {
}
