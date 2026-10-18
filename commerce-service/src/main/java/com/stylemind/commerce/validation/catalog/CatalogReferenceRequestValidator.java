package com.stylemind.commerce.validation.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.dto.catalog.CatalogReferenceRequest;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CatalogReferenceRequestValidator {

    private static final Set<String> FIELDS = Set.of("name", "slug", "is_active");
    private static final String SLUG_PATTERN = "[a-z0-9][a-z0-9-]{0,63}";

    public CatalogReferenceRequest command(JsonNode body) {
        if (body == null || !body.isObject() || body.size() != FIELDS.size()) {
            throw invalidRequest();
        }
        for (Iterator<String> names = body.fieldNames(); names.hasNext();) {
            if (!FIELDS.contains(names.next())) {
                throw invalidRequest();
            }
        }
        JsonNode nameNode = body.get("name");
        JsonNode slugNode = body.get("slug");
        JsonNode activeNode = body.get("is_active");
        if (nameNode == null || !nameNode.isTextual()
                || slugNode == null || !slugNode.isTextual()
                || activeNode == null || !activeNode.isBoolean()) {
            throw invalidRequest();
        }
        String name = trimText(nameNode.textValue(), 100);
        String slug = slugNode.textValue().strip().toLowerCase(Locale.ROOT);
        if (!slug.matches(SLUG_PATTERN) || slug.contains("--")) {
            throw invalidRequest();
        }
        return new CatalogReferenceRequest(name, slug, activeNode.booleanValue());
    }

    private String trimText(String raw, int maxCodePoints) {
        String value = raw.strip();
        if (value.isEmpty() || value.codePointCount(0, value.length()) > maxCodePoints
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw invalidRequest();
        }
        return value;
    }

    private ApiOperationException invalidRequest() {
        return new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "Request is missing or malformed");
    }
}
