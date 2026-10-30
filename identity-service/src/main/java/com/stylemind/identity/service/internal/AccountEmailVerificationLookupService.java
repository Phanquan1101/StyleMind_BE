package com.stylemind.identity.service.internal;

import com.stylemind.identity.dto.internal.AccountEmailVerificationLookupResponse;
import java.util.Collection;
import java.util.UUID;

public interface AccountEmailVerificationLookupService {
    AccountEmailVerificationLookupResponse lookup(Collection<UUID> accountIds);
    AccountEmailVerificationLookupResponse lookupByEmail(String email);
}
