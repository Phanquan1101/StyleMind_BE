package com.stylemind.stylist.service.recommendation.internal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Explicit presence, including null, overrides an explicitly selected saved requirement. */
public final class ContextResolver {
    public Map<String, String> resolve(Map<String, String> explicit, Map<String, String> selectedRequirement) {
        Objects.requireNonNull(explicit); Objects.requireNonNull(selectedRequirement);
        Map<String, String> result = new LinkedHashMap<>(selectedRequirement);
        result.putAll(explicit);
        return Collections.unmodifiableMap(result);
    }
}
