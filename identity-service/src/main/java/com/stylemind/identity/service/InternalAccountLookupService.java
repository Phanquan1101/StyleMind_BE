package com.stylemind.identity.service;

import com.stylemind.identity.dto.internal.InternalAccountLookupResponse;
import java.util.Collection;
import java.util.UUID;

public interface InternalAccountLookupService {
    InternalAccountLookupResponse lookup(Collection<UUID> accountIds);
}
