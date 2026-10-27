package com.stylemind.commerce.validation.internal;

import com.stylemind.commerce.dto.internal.InternalVariantLookupRequest;
import com.stylemind.commerce.errors.ApiOperationException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class InternalLookupRequestValidator {

    private static final int MAX_IDS = 100;

    /** 1..100 valid UUIDs; duplicates are collapsed and the order of first appearance is kept. */
    public List<UUID> variantIds(InternalVariantLookupRequest request) {
        if (request == null || request.variantIds() == null || request.variantIds().isEmpty()
                || request.variantIds().size() > MAX_IDS) {
            throw invalidRequest();
        }
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        for (String raw : request.variantIds()) {
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
        return new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "Request is missing or malformed");
    }
}
