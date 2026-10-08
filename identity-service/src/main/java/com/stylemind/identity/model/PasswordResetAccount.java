package com.stylemind.identity.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PasswordResetAccount(UUID accountId, String accountStatus, OffsetDateTime emailVerifiedAt) {
}
