package com.stylemind.commerce.validation.branch;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.model.branch.BranchCommand;
import com.stylemind.commerce.model.branch.BranchStatus;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class BranchRequestValidator {

    private static final Set<String> FIELDS = Set.of(
            "branch_code", "name", "address_line", "contact_phone", "branch_status");
    private static final String CODE_PATTERN = "[A-Z0-9][A-Z0-9_-]{1,31}";

    public BranchCommand command(JsonNode body) {
        requireExactFields(body);
        String code = text(body, "branch_code", false).strip().toUpperCase(Locale.ROOT);
        if (!code.matches(CODE_PATTERN)) {
            throw invalidRequest();
        }
        return new BranchCommand(code, boundedText(text(body, "name", false), 1, 160),
                boundedText(text(body, "address_line", false), 1, 500),
                nullableBoundedText(body, "contact_phone", 32),
                enumValue(text(body, "branch_status", false), BranchStatus.class));
    }

    public UUID pathId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (RuntimeException exception) {
            throw invalidRequest();
        }
    }

    private void requireExactFields(JsonNode body) {
        if (body == null || !body.isObject() || body.size() != FIELDS.size()) {
            throw invalidRequest();
        }
        for (Iterator<String> names = body.fieldNames(); names.hasNext();) {
            if (!FIELDS.contains(names.next())) {
                throw invalidRequest();
            }
        }
    }

    private String text(JsonNode body, String field, boolean nullable) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            if (nullable) {
                return null;
            }
            throw invalidRequest();
        }
        if (!node.isTextual()) {
            throw invalidRequest();
        }
        return node.textValue();
    }

    private String nullableBoundedText(JsonNode body, String field, int max) {
        String raw = text(body, field, true);
        return raw == null || raw.isBlank() ? null : boundedText(raw, 1, max);
    }

    private String boundedText(String raw, int min, int max) {
        String value = raw.strip();
        int length = value.codePointCount(0, value.length());
        if (length < min || length > max || value.codePoints().anyMatch(Character::isISOControl)) {
            throw invalidRequest();
        }
        return value;
    }

    private <E extends Enum<E>> E enumValue(String raw, Class<E> enumType) {
        try {
            return Enum.valueOf(enumType, raw);
        } catch (RuntimeException exception) {
            throw invalidRequest();
        }
    }

    private ApiOperationException invalidRequest() {
        return new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "Request is missing or malformed");
    }
}
