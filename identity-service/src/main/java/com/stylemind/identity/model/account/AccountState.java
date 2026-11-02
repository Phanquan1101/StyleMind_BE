package com.stylemind.identity.model.account;

import java.util.UUID;

public record AccountState(UUID accountId, String accountStatus) {
}
