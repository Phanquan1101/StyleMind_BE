package com.stylemind.identity.validation.users;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.identity.errors.ApiOperationException;
import com.stylemind.identity.model.users.ManageUserListCriteria;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ManageUsersRequestValidator {

    private static final Set<String> QUERY_FIELDS = Set.of("status", "role", "email_contains", "page", "size");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "PENDING_VERIFICATION", "SUSPENDED");
    private static final Set<String> MUTABLE_STATUSES = Set.of("ACTIVE", "SUSPENDED");
    private static final Set<String> ROLES = Set.of("CUSTOMER", "ADMIN");
    private static final String UUID_PATTERN = "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    public ManageUserListCriteria listCriteria(HttpServletRequest request) {
        Map<String, String[]> parameters = request.getParameterMap();
        if (!QUERY_FIELDS.containsAll(parameters.keySet())) {
            throw invalidRequest();
        }
        parameters.forEach((key, values) -> {
            if (values == null || values.length != 1 || values[0] == null) {
                throw invalidRequest();
            }
        });

        String status = parameter(parameters, "status");
        if (status != null && !STATUSES.contains(status)) {
            throw invalidRequest();
        }
        String role = parameter(parameters, "role");
        if (role != null && !ROLES.contains(role)) {
            throw invalidRequest();
        }
        String email = parameter(parameters, "email_contains");
        if (email != null) {
            email = email.strip();
            if (email.isEmpty() || email.codePointCount(0, email.length()) > 254) {
                throw invalidRequest();
            }
        }
        int page = parseInteger(parameter(parameters, "page"), 0);
        int size = parseInteger(parameter(parameters, "size"), 20);
        if (page < 0 || size < 1 || size > 100) {
            throw invalidRequest();
        }
        return new ManageUserListCriteria(status, role, email, page, size);
    }

    public UUID accountId(String raw) {
        if (raw == null || !raw.matches(UUID_PATTERN)) {
            throw invalidRequest();
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            throw invalidRequest();
        }
    }

    public String requestedStatus(JsonNode body) {
        JsonNode value = exactObjectProperty(body, "account_status");
        if (!value.isTextual() || !MUTABLE_STATUSES.contains(value.textValue())) {
            throw invalidRequest();
        }
        return value.textValue();
    }

    public Set<String> requestedRoles(JsonNode body) {
        JsonNode values = exactObjectProperty(body, "roles");
        if (!values.isArray() || values.isEmpty()) {
            throw invalidRequest();
        }
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        for (JsonNode value : values) {
            if (!value.isTextual() || !ROLES.contains(value.textValue()) || !roles.add(value.textValue())) {
                throw invalidRequest();
            }
        }
        return Set.copyOf(roles);
    }

    private JsonNode exactObjectProperty(JsonNode body, String expected) {
        if (body == null || !body.isObject() || body.size() != 1 || !body.has(expected)) {
            throw invalidRequest();
        }
        return body.get(expected);
    }

    private String parameter(Map<String, String[]> parameters, String name) {
        String[] values = parameters.get(name);
        return values == null ? null : values[0];
    }

    private int parseInteger(String raw, int defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        if (raw.isEmpty() || !raw.chars().allMatch(Character::isDigit)) {
            throw invalidRequest();
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            throw invalidRequest();
        }
    }

    private ApiOperationException invalidRequest() {
        return new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "Request is missing or malformed", 0);
    }
}
