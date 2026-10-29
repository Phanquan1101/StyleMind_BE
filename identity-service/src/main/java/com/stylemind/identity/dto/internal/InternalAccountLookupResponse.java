package com.stylemind.identity.dto.internal;

import java.util.List;

public record InternalAccountLookupResponse(
        List<InternalAccountItem> items) {
}
