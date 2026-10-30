package com.stylemind.identity.dto.internal;

import java.util.List;

public record AccountEmailVerificationLookupResponse(List<AccountEmailVerificationItem> items) {
    public AccountEmailVerificationLookupResponse { items = List.copyOf(items); }
}
