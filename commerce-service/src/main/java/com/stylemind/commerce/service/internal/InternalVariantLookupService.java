package com.stylemind.commerce.service.internal;

import com.stylemind.commerce.dto.internal.InternalVariantLookupResponse;
import java.util.Collection;
import java.util.UUID;

public interface InternalVariantLookupService {

    InternalVariantLookupResponse lookup(Collection<UUID> variantIds);
}
