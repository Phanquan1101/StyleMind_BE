package com.stylemind.identity.model;

import java.util.UUID;

public record AccountState(UUID accountId, String accountStatus) {
}
