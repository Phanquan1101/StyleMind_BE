package com.stylemind.identity.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ManagedUserSummary(
        UUID accountId,
        String email,
        String accountStatus,
        OffsetDateTime emailVerifiedAt,
        OffsetDateTime registeredAt,
        List<String> roles) {
}
