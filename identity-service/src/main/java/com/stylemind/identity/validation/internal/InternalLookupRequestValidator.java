package com.stylemind.identity.validation.internal;

import com.stylemind.identity.dto.internal.InternalAccountLookupRequest;
import com.stylemind.identity.errors.ApiOperationException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import com.stylemind.identity.errors.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class InternalLookupRequestValidator {

    private static final int MAX_IDS = 100;

    /** 1..100 valid UUIDs; duplicates are collapsed and the order of first appearance is kept. */
    public List<UUID> accountIds(InternalAccountLookupRequest request) {
        if (request == null || request.accountIds() == null || request.accountIds().isEmpty()
                || request.accountIds().size() > MAX_IDS) {
            throw invalidRequest();
        }
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        for (String raw : request.accountIds()) {
            ids.add(uuid(raw));
        }
        return List.copyOf(ids);
    }

    private UUID uuid(String raw) {
        if (raw == null) {
            throw invalidRequest();
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            throw invalidRequest();
        }
    }

    private ApiOperationException invalidRequest() {
        return new ApiOperationException(ErrorCode.INVALID_REQUEST, "Request is missing or malformed");
    }
}
