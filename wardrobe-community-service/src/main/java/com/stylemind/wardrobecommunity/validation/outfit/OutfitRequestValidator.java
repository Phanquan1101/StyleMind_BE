package com.stylemind.wardrobecommunity.validation.outfit;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.wardrobecommunity.dto.outfit.OutfitCreateCommand;
import com.stylemind.wardrobecommunity.dto.outfit.OutfitListCriteria;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class OutfitRequestValidator {

    public static final int MAX_ITEMS = 10;
    private static final int MAX_NAME_CODE_POINTS = 100;
    private static final Set<String> CREATION_SOURCES = Set.of("MANUAL", "AI");
    private static final Set<String> LIST_FIELDS = Set.of("page", "size");

    public UUID pathId(String raw) {
        return uuid(raw);
    }

    public OutfitListCriteria list(HttpServletRequest request) {
        Map<String, String[]> params = request.getParameterMap();
        if (!LIST_FIELDS.containsAll(params.keySet())) {
            throw invalidRequest();
        }
        params.forEach((key, values) -> {
            if (values == null || values.length != 1 || values[0] == null) {
                throw invalidRequest();
            }
        });
        int page = parseInt(params.get("page"), 0);
        int size = parseInt(params.get("size"), 20);
        if (page < 0 || size < 1 || size > 100) {
            throw invalidRequest();
        }
        return new OutfitListCriteria(page, size);
    }

    public OutfitCreateCommand create(JsonNode body) {
        requireFields(body, Set.of("name", "creation_source", "items"));
        String name = name(body.get("name"));
        JsonNode source = body.get("creation_source");
        if (!source.isTextual() || !CREATION_SOURCES.contains(source.textValue())) {
            throw invalidRequest();
        }
        JsonNode items = body.get("items");
        if (!items.isArray() || items.isEmpty() || items.size() > MAX_ITEMS) {
            throw invalidRequest();
        }
        LinkedHashSet<UUID> variantIds = new LinkedHashSet<>();
        for (JsonNode item : items) {
            requireFields(item, Set.of("variant_id"));
            if (!variantIds.add(variantIdValue(item.get("variant_id")))) {
                throw invalidRequest();
            }
        }
        return new OutfitCreateCommand(name, source.textValue(), new ArrayList<>(variantIds));
    }

    public String rename(JsonNode body) {
        requireFields(body, Set.of("name"));
        return name(body.get("name"));
    }

    public UUID variantId(JsonNode body) {
        requireFields(body, Set.of("variant_id"));
        return variantIdValue(body.get("variant_id"));
    }

    private String name(JsonNode node) {
        if (node == null || !node.isTextual()) {
            throw invalidRequest();
        }
        String value = trimUnicodeWhitespace(node.textValue());
        int codePoints = value.codePointCount(0, value.length());
        if (codePoints < 1 || codePoints > MAX_NAME_CODE_POINTS) {
            throw invalidRequest();
        }
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (Character.getType(codePoint) == Character.CONTROL) {
                throw invalidRequest();
            }
            offset += Character.charCount(codePoint);
        }
        return value;
    }

    private UUID variantIdValue(JsonNode node) {
        if (node == null || !node.isTextual()) {
            throw invalidRequest();
        }
        return uuid(node.textValue());
    }

    private void requireFields(JsonNode body, Set<String> fields) {
        if (body == null || !body.isObject() || body.size() != fields.size()) {
            throw invalidRequest();
        }
        for (Iterator<String> names = body.fieldNames(); names.hasNext();) {
            if (!fields.contains(names.next())) {
                throw invalidRequest();
            }
        }
    }

    private int parseInt(String[] raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw[0]);
        } catch (NumberFormatException exception) {
            throw invalidRequest();
        }
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

    private String trimUnicodeWhitespace(String value) {
        int start = 0;
        int end = value.length();
        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!isUnicodeWhitespace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (end > start) {
            int codePoint = value.codePointBefore(end);
            if (!isUnicodeWhitespace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return value.substring(start, end);
    }

    private boolean isUnicodeWhitespace(int codePoint) {
        return (codePoint >= 0x0009 && codePoint <= 0x000D)
                || codePoint == 0x0020 || codePoint == 0x0085 || codePoint == 0x00A0
                || codePoint == 0x1680 || (codePoint >= 0x2000 && codePoint <= 0x200A)
                || codePoint == 0x2028 || codePoint == 0x2029 || codePoint == 0x202F
                || codePoint == 0x205F || codePoint == 0x3000;
    }

    private ApiOperationException invalidRequest() {
        return new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "Request is missing or malformed");
    }
}
