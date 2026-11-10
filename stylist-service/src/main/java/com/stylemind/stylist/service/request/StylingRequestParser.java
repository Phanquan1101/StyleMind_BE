package com.stylemind.stylist.service.request;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.stylemind.stylist.dto.request.CreateStylingRequestRequest;
import com.stylemind.stylist.dto.request.CreateStylingRequestRequest.ConstraintInput;
import com.stylemind.stylist.dto.request.StylingConstraintKind;
import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class StylingRequestParser {

    private static final Set<String> REQUEST_FIELDS = Set.of("message", "source_message_id", "occasion",
            "intended_use", "style_context", "general_weather_context", "max_budget_vnd", "budget_is_hard",
            "constraints");
    private static final Set<String> CONSTRAINT_FIELDS = Set.of(
            "constraint_kind", "catalog_entity_id", "text_value", "is_hard");
    private static final int MAX_CONSTRAINTS = 20;

    private final ObjectMapper strictMapper;

    public StylingRequestParser() {
        JsonFactory factory = JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
        strictMapper = JsonMapper.builder(factory)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
    }

    public CreateStylingRequestRequest parse(byte[] json) {
        try {
            JsonNode root = strictMapper.readTree(json);
            if (root == null || !root.isObject()) throw invalidRequest();
            rejectUnknownFields(root, REQUEST_FIELDS);

            String message = optionalText(root, "message", 2_000, true);
            UUID sourceMessageId = optionalUuid(root, "source_message_id");
            if (message != null && sourceMessageId != null) throw invalidRequest();

            String occasion = optionalText(root, "occasion", 128, true);
            String intendedUse = optionalText(root, "intended_use", 128, true);
            String styleContext = optionalText(root, "style_context", 128, true);
            String weatherContext = optionalText(root, "general_weather_context", 1_000, false);
            Long maxBudget = optionalNonnegativeLong(root, "max_budget_vnd");
            boolean budgetIsHard = optionalBoolean(root, "budget_is_hard", false);
            if (budgetIsHard && maxBudget == null) throw invalidContext();

            var constraints = constraints(root);
            if (message == null && sourceMessageId == null && occasion == null && intendedUse == null
                    && styleContext == null && weatherContext == null) {
                throw invalidContext();
            }
            return new CreateStylingRequestRequest(message, sourceMessageId, occasion, intendedUse, styleContext,
                    weatherContext, maxBudget, budgetIsHard, constraints);
        } catch (IOException exception) {
            throw invalidRequest();
        }
    }

    private static java.util.List<ConstraintInput> constraints(JsonNode root) {
        JsonNode node = root.get("constraints");
        if (node == null) return java.util.List.of();
        if (!node.isArray() || node.size() > MAX_CONSTRAINTS) throw invalidRequest();

        var parsed = new ArrayList<ConstraintInput>(node.size());
        var canonicalEntries = new HashSet<String>();
        for (JsonNode item : node) {
            if (!item.isObject()) throw invalidRequest();
            rejectUnknownFields(item, CONSTRAINT_FIELDS);
            JsonNode kindNode = item.get("constraint_kind");
            if (kindNode == null || !kindNode.isTextual()) throw invalidRequest();
            StylingConstraintKind kind;
            try {
                kind = StylingConstraintKind.valueOf(kindNode.textValue());
            } catch (IllegalArgumentException exception) {
                throw invalidRequest();
            }

            UUID catalogId = optionalUuid(item, "catalog_entity_id");
            String textValue = optionalText(item, "text_value", 64, true);
            JsonNode hardNode = item.get("is_hard");
            if (hardNode == null || !hardNode.isBoolean()) throw invalidRequest();
            boolean hard = hardNode.booleanValue();
            if (kind == StylingConstraintKind.EXCLUDE_COLOR ? catalogId != null || textValue == null
                    : catalogId == null || textValue != null) {
                throw invalidRequest();
            }
            String valueKey = catalogId == null ? textValue.toUpperCase(Locale.ROOT) : catalogId.toString();
            if (!canonicalEntries.add(kind.name() + ":" + valueKey)) throw invalidRequest();
            parsed.add(new ConstraintInput(kind, catalogId, textValue, hard));
        }
        return java.util.List.copyOf(parsed);
    }

    private static Long optionalNonnegativeLong(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) return null;
        if (!node.isIntegralNumber() || !node.canConvertToLong()) throw invalidRequest();
        long value = node.longValue();
        if (value < 0) throw invalidRequest();
        return value;
    }

    private static boolean optionalBoolean(JsonNode root, String field, boolean defaultValue) {
        JsonNode node = root.get(field);
        if (node == null) return defaultValue;
        if (!node.isBoolean()) throw invalidRequest();
        return node.booleanValue();
    }

    private static UUID optionalUuid(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) return null;
        if (!node.isTextual()) throw invalidRequest();
        try {
            String raw = node.textValue();
            UUID id = UUID.fromString(raw);
            if (!id.toString().equalsIgnoreCase(raw)) throw invalidRequest();
            return id;
        } catch (IllegalArgumentException exception) {
            throw invalidRequest();
        }
    }

    private static String optionalText(JsonNode root, String field, int maxCodePoints, boolean rejectControls) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) return null;
        if (!node.isTextual()) throw invalidRequest();
        String value = trimUnicodeWhitespace(node.textValue());
        int codePoints = value.codePointCount(0, value.length());
        if (value.isEmpty() || codePoints > maxCodePoints
                || value.indexOf('\0') >= 0
                || rejectControls && value.codePoints().anyMatch(Character::isISOControl)) {
            throw invalidRequest();
        }
        return value;
    }

    private static String trimUnicodeWhitespace(String value) {
        int start = 0;
        int end = value.length();
        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!isUnicodeWhitespace(codePoint)) break;
            start += Character.charCount(codePoint);
        }
        while (end > start) {
            int codePoint = value.codePointBefore(end);
            if (!isUnicodeWhitespace(codePoint)) break;
            end -= Character.charCount(codePoint);
        }
        return value.substring(start, end);
    }

    private static boolean isUnicodeWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private static void rejectUnknownFields(JsonNode node, Set<String> allowed) {
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) if (!allowed.contains(names.next())) throw invalidRequest();
    }

    private static StylistApiException invalidRequest() {
        return new StylistApiException(ErrorCode.INVALID_REQUEST);
    }

    private static StylistApiException invalidContext() {
        return new StylistApiException(ErrorCode.INVALID_CONTEXT);
    }
}
