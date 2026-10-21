package com.stylemind.identity.validation.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.identity.dto.profile.PatchMyProfileRequest;
import com.stylemind.identity.dto.profile.ReplaceStyleProfileRequest;
import com.stylemind.identity.errors.ApiOperationException;
import com.stylemind.identity.model.profile.StyleProfileCommand;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public final class ProfileRequestValidator {

    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");
    private static final Pattern HYPHENATED_UUID = Pattern.compile(
            "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final BigDecimal MIN_HEIGHT_CM = new BigDecimal("50.0");
    private static final BigDecimal MAX_HEIGHT_CM = new BigDecimal("250.0");
    private static final BigDecimal MIN_WEIGHT_KG = new BigDecimal("20.0");
    private static final BigDecimal MAX_WEIGHT_KG = new BigDecimal("300.0");
    private static final int MAX_COLLECTION_SIZE = 50;

    public String validatePatch(PatchMyProfileRequest request) {
        if (request == null || !request.getUnknownProperties().isEmpty() || !request.isDisplayNamePresent()) {
            throw invalidRequest();
        }
        return displayName(request.getDisplayName());
    }

    public StyleProfileCommand validateReplacement(ReplaceStyleProfileRequest request) {
        if (request == null || !request.getUnknownProperties().isEmpty()
                || !request.isFitPreferencePresent() || !request.isHeightCmPresent()
                || !request.isUsualTopSizePresent() || !request.isUsualBottomSizePresent()
                || !request.isShoeSizePresent() || !request.isStyleCodesPresent()
                || !request.isColorCodesPresent() || !request.isBrandIdsPresent()) {
            throw invalidRequest();
        }

        return new StyleProfileCommand(
                optionalCode(request.getFitPreference()),
                height(request.getHeightCm()),
                weight(request.getWeightKg()),
                optionalLabel(request.getUsualTopSize(), 32),
                optionalLabel(request.getUsualBottomSize(), 32),
                optionalLabel(request.getShoeSize(), 32),
                codes(request.getStyleCodes()),
                codes(request.getColorCodes()),
                brandIds(request.getBrandIds()));
    }

    private String displayName(JsonNode node) {
        if (node == null || !node.isTextual()) {
            throw invalidRequest();
        }
        String value = trimUnicodeWhitespace(node.textValue());
        validateNonBlank(value, 100);
        rejectControlCharacters(value);
        return value;
    }

    private String optionalCode(JsonNode node) {
        if (isJsonNull(node)) {
            return null;
        }
        if (!node.isTextual()) {
            throw invalidRequest();
        }
        String value = trimUnicodeWhitespace(node.textValue());
        if (!CODE.matcher(value).matches()) {
            throw invalidRequest();
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private String optionalLabel(JsonNode node, int maxCodePoints) {
        if (isJsonNull(node)) {
            return null;
        }
        if (!node.isTextual()) {
            throw invalidRequest();
        }
        String value = trimUnicodeWhitespace(node.textValue());
        validateNonBlank(value, maxCodePoints);
        rejectControlCharacters(value);
        return value;
    }

    private BigDecimal height(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.scale() > 1 || value.compareTo(MIN_HEIGHT_CM) < 0 || value.compareTo(MAX_HEIGHT_CM) > 0) {
            throw invalidRequest();
        }
        return value;
    }

    private BigDecimal weight(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.scale() > 1 || value.compareTo(MIN_WEIGHT_KG) < 0 || value.compareTo(MAX_WEIGHT_KG) > 0) {
            throw invalidRequest();
        }
        return value;
    }

    private List<String> codes(JsonNode node) {
        if (node == null || !node.isArray() || node.size() > MAX_COLLECTION_SIZE) {
            throw invalidRequest();
        }
        List<String> canonical = new ArrayList<>(node.size());
        Set<String> unique = new HashSet<>();
        for (JsonNode item : node) {
            if (item == null || !item.isTextual()) {
                throw invalidRequest();
            }
            String value = trimUnicodeWhitespace(item.textValue());
            if (!CODE.matcher(value).matches()) {
                throw invalidRequest();
            }
            String normalized = value.toUpperCase(Locale.ROOT);
            if (!unique.add(normalized)) {
                throw invalidRequest();
            }
            canonical.add(normalized);
        }
        return List.copyOf(canonical);
    }

    private List<UUID> brandIds(JsonNode node) {
        if (node == null || !node.isArray() || node.size() > MAX_COLLECTION_SIZE) {
            throw invalidRequest();
        }
        List<UUID> values = new ArrayList<>(node.size());
        Set<UUID> unique = new HashSet<>();
        for (JsonNode item : node) {
            if (item == null || !item.isTextual()) {
                throw invalidRequest();
            }
            String value = item.textValue();
            if (!HYPHENATED_UUID.matcher(value).matches()) {
                throw invalidRequest();
            }
            UUID id;
            try {
                id = UUID.fromString(value);
            } catch (IllegalArgumentException exception) {
                throw invalidRequest();
            }
            if (!id.toString().equalsIgnoreCase(value) || !unique.add(id)) {
                throw invalidRequest();
            }
            values.add(id);
        }
        return List.copyOf(values);
    }

    private void validateNonBlank(String value, int maxCodePoints) {
        int codePoints = value.codePointCount(0, value.length());
        if (codePoints < 1 || codePoints > maxCodePoints) {
            throw invalidRequest();
        }
    }

    private void rejectControlCharacters(String value) {
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (Character.getType(codePoint) == Character.CONTROL) {
                throw invalidRequest();
            }
            offset += Character.charCount(codePoint);
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

    private boolean isJsonNull(JsonNode node) {
        return node == null || node.isNull();
    }

    private ApiOperationException invalidRequest() {
        return new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "Request is missing or malformed", 0);
    }
}
