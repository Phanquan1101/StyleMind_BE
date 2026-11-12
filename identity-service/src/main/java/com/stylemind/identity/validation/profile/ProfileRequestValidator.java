package com.stylemind.identity.validation.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.identity.dto.profile.PatchMyProfileRequest;
import com.stylemind.identity.dto.profile.ReplaceStyleProfileRequest;
import com.stylemind.identity.errors.ApiOperationException;
import com.stylemind.identity.errors.FieldError;
import com.stylemind.identity.dto.profile.StyleProfileUpdateRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import com.stylemind.identity.errors.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public final class ProfileRequestValidator {

    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");
    private static final Pattern HYPHENATED_UUID = Pattern.compile(
            "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final BigDecimal MIN_HEIGHT_CM = new BigDecimal("100");
    private static final BigDecimal MAX_HEIGHT_CM = new BigDecimal("230");
    private static final BigDecimal MIN_WEIGHT_KG = new BigDecimal("30");
    private static final BigDecimal MAX_WEIGHT_KG = new BigDecimal("200");
    private static final int MAX_COLLECTION_SIZE = 50;

    // Allowed clothing/shoe sizes. Keep in sync with the dropdowns in the web app (profile form).
    private static final List<String> TOP_SIZES = List.of("XS", "S", "M", "L", "XL", "2XL", "3XL", "4XL");
    private static final Set<String> BOTTOM_SIZES = buildBottomSizes();
    private static final Set<String> SHOE_SIZES = buildRange(34, 46, 1);

    private static Set<String> buildRange(int from, int to, int step) {
        Set<String> values = new java.util.LinkedHashSet<>();
        for (int value = from; value <= to; value += step) {
            values.add(Integer.toString(value));
        }
        return values;
    }

    private static Set<String> buildBottomSizes() {
        Set<String> values = new java.util.LinkedHashSet<>(TOP_SIZES);
        values.addAll(buildRange(24, 40, 1));
        values.addAll(buildRange(0, 18, 2));
        return values;
    }

    public String validatePatch(PatchMyProfileRequest request) {
        if (request == null || !request.getUnknownProperties().isEmpty() || !request.isDisplayNamePresent()) {
            throw invalidRequest();
        }
        List<FieldError> errors = new ArrayList<>();
        String name = collect(errors, "display_name", "Tên hiển thị phải từ 1 đến 100 ký tự và không chứa ký tự điều khiển",
                () -> displayName(request.getDisplayName()));
        throwIfAny(errors);
        return name;
    }

    public StyleProfileUpdateRequest validateReplacement(ReplaceStyleProfileRequest request) {
        if (request == null || !request.getUnknownProperties().isEmpty()
                || !request.isFitPreferencePresent() || !request.isHeightCmPresent()
                || !request.isUsualTopSizePresent() || !request.isUsualBottomSizePresent()
                || !request.isShoeSizePresent() || !request.isStyleCodesPresent()
                || !request.isColorCodesPresent() || !request.isBrandIdsPresent()) {
            throw invalidRequest();
        }

        List<FieldError> errors = new ArrayList<>();
        String fit = collect(errors, "fit_preference", "Dáng mặc vừa không hợp lệ", () -> optionalCode(request.getFitPreference()));
        BigDecimal height = collect(errors, "height_cm", "Chiều cao phải từ 100 đến 230 cm (tối đa 1 chữ số thập phân)",
                () -> height(request.getHeightCm()));
        BigDecimal weight = collect(errors, "weight_kg", "Cân nặng phải từ 30 đến 200 kg (tối đa 1 chữ số thập phân)",
                () -> weight(request.getWeightKg()));
        String top = collect(errors, "usual_top_size", "Cỡ áo phải là một trong: " + String.join(", ", TOP_SIZES),
                () -> size(request.getUsualTopSize(), new java.util.LinkedHashSet<>(TOP_SIZES)));
        String bottom = collect(errors, "usual_bottom_size", "Cỡ quần/váy phải là chữ (XS–4XL), số 24–40 hoặc số chẵn 0–18",
                () -> size(request.getUsualBottomSize(), BOTTOM_SIZES));
        String shoe = collect(errors, "shoe_size", "Cỡ giày phải là số nguyên từ 34 đến 46",
                () -> size(request.getShoeSize(), SHOE_SIZES));
        List<String> styles = collect(errors, "style_codes", "Danh sách phong cách không hợp lệ", () -> codes(request.getStyleCodes()));
        List<String> colors = collect(errors, "color_codes", "Danh sách màu không hợp lệ", () -> codes(request.getColorCodes()));
        List<UUID> brands = collect(errors, "brand_ids", "Danh sách thương hiệu không hợp lệ", () -> brandIds(request.getBrandIds()));
        throwIfAny(errors);
        return new StyleProfileUpdateRequest(fit, height, weight, top, bottom, shoe, styles, colors, brands);
    }

    private <T> T collect(List<FieldError> errors, String field, String message, Supplier<T> parser) {
        try {
            return parser.get();
        } catch (ApiOperationException exception) {
            errors.add(new FieldError(field, "INVALID_VALUE", message));
            return null;
        }
    }

    private static void throwIfAny(List<FieldError> errors) {
        if (!errors.isEmpty()) {
            throw ApiOperationException.invalidFields(errors);
        }
    }

    /** A size from a fixed list; JSON null means "not set". Common aliases (XXL, XXXL) are normalised. */
    private String size(JsonNode node, Set<String> allowed) {
        if (isJsonNull(node)) {
            return null;
        }
        if (!node.isTextual()) {
            throw invalidRequest();
        }
        String value = trimUnicodeWhitespace(node.textValue()).toUpperCase(Locale.ROOT);
        value = switch (value) {
            case "XXL" -> "2XL";
            case "XXXL" -> "3XL";
            case "XXXXL" -> "4XL";
            default -> value;
        };
        if (!allowed.contains(value)) {
            throw invalidRequest();
        }
        return value;
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
        return new ApiOperationException(ErrorCode.INVALID_REQUEST, "Request is missing or malformed");
    }
}
