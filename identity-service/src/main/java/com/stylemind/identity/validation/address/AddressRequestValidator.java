package com.stylemind.identity.validation.address;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.identity.directory.VietnamAdministrativeDirectory;
import com.stylemind.identity.errors.ApiOperationException;
import com.stylemind.identity.errors.FieldError;
import com.stylemind.identity.dto.address.AddressCreateRequest;
import com.stylemind.identity.dto.address.AddressPatchRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import com.stylemind.identity.errors.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public final class AddressRequestValidator {
    private static final Set<String> FIELDS = Set.of("label", "recipient_name", "recipient_phone", "address_line", "ward_code");
    private final VietnamPhoneValidator phoneValidator;
    private final VietnamAdministrativeDirectory directory;

    public AddressRequestValidator(VietnamPhoneValidator phoneValidator, VietnamAdministrativeDirectory directory) {
        this.phoneValidator = phoneValidator;
        this.directory = directory;
    }

    public AddressCreateRequest validateCreate(JsonNode body) {
        requireObject(body);
        checkFields(body);
        List<FieldError> errors = new ArrayList<>();
        String label = collect(errors, "label", "Tên gợi nhớ tối đa 50 ký tự", () -> optionalNullableText(body, "label", 50, false));
        String name = collect(errors, "recipient_name", "Vui lòng nhập tên người nhận (tối đa 100 ký tự)",
                () -> requiredText(body, "recipient_name", 100));
        String phone = collect(errors, "recipient_phone", "Số điện thoại Việt Nam không hợp lệ (ví dụ 0912345678)",
                () -> phoneValidator.canonicalize(requiredTextRaw(body, "recipient_phone", 64)));
        String line = collect(errors, "address_line", "Vui lòng nhập địa chỉ (số nhà, đường), tối đa 500 ký tự",
                () -> requiredText(body, "address_line", 500));
        String ward = collect(errors, "ward_code", "Vui lòng chọn phường/xã hợp lệ", () -> optionalNullableText(body, "ward_code", 16, true));
        throwIfAny(errors);
        return new AddressCreateRequest(label, name, phone, line, ward);
    }

    public AddressPatchRequest validatePatch(JsonNode body) {
        requireObject(body);
        checkFields(body);
        if (body.isEmpty()) {
            throw invalid();
        }
        boolean hasLabel = body.has("label");
        boolean hasName = body.has("recipient_name");
        boolean hasPhone = body.has("recipient_phone");
        boolean hasLine = body.has("address_line");
        boolean hasWard = body.has("ward_code");
        List<FieldError> errors = new ArrayList<>();
        String label = hasLabel ? collect(errors, "label", "Tên gợi nhớ tối đa 50 ký tự", () -> optionalNullableText(body, "label", 50, false)) : null;
        String name = hasName ? collect(errors, "recipient_name", "Vui lòng nhập tên người nhận (tối đa 100 ký tự)",
                () -> requiredText(body, "recipient_name", 100)) : null;
        String phone = hasPhone ? collect(errors, "recipient_phone", "Số điện thoại Việt Nam không hợp lệ (ví dụ 0912345678)",
                () -> phoneValidator.canonicalize(requiredTextRaw(body, "recipient_phone", 64))) : null;
        String line = hasLine ? collect(errors, "address_line", "Vui lòng nhập địa chỉ (số nhà, đường), tối đa 500 ký tự",
                () -> requiredText(body, "address_line", 500)) : null;
        String ward = hasWard ? collect(errors, "ward_code", "Vui lòng chọn phường/xã hợp lệ",
                () -> optionalNullableText(body, "ward_code", 16, true)) : null;
        throwIfAny(errors);
        return new AddressPatchRequest(hasLabel, label, hasName, name, hasPhone, phone,
                hasLine, line, hasWard, ward);
    }

    private static <T> T collect(List<FieldError> errors, String field, String message, Supplier<T> parser) {
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

    private void checkFields(JsonNode body) {
        Set<String> unknown = new HashSet<>();
        Iterator<String> names = body.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!FIELDS.contains(name)) {
                unknown.add(name);
            }
        }
        if (!unknown.isEmpty()) {
            throw invalid();
        }
    }

    private String requiredText(JsonNode body, String field, int max) {
        if (!body.has(field) || body.get(field).isNull()) {
            throw invalid();
        }
        return cleanText(body.get(field), max, false);
    }

    private String requiredTextRaw(JsonNode body, String field, int max) {
        if (!body.has(field) || !body.get(field).isTextual()) {
            throw invalid();
        }
        String value = body.get(field).asText();
        if (codePoints(value) > max || hasControl(value)) {
            throw invalid();
        }
        return value;
    }

    private String optionalNullableText(JsonNode body, String field, int max, boolean ward) {
        if (!body.has(field) || body.get(field).isNull()) {
            return null;
        }
        if (ward) {
            if (!body.get(field).isTextual()) throw invalid();
            String value = trimUnicode(body.get(field).asText());
            if (value.isEmpty() || hasControl(value) || !directory.containsWard(value)) throw invalid();
            return value;
        }
        return cleanText(body.get(field), max, true);
    }

    private String cleanText(JsonNode node, int max, boolean optional) {
        if (!node.isTextual()) {
            throw invalid();
        }
        String value = trimUnicode(node.asText());
        int count = codePoints(value);
        if (count < 1 || count > max || hasControl(value)) {
            throw invalid();
        }
        return value;
    }

    private static String trimUnicode(String value) {
        int start = 0;
        int end = value.length();
        while (start < end) {
            int cp = value.codePointAt(start);
            if (!Character.isWhitespace(cp) && !Character.isSpaceChar(cp)) break;
            start += Character.charCount(cp);
        }
        while (start < end) {
            int cp = value.codePointBefore(end);
            if (!Character.isWhitespace(cp) && !Character.isSpaceChar(cp)) break;
            end -= Character.charCount(cp);
        }
        return value.substring(start, end);
    }

    private static int codePoints(String value) { return value.codePointCount(0, value.length()); }

    private static boolean hasControl(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }

    private static void requireObject(JsonNode body) {
        if (body == null || !body.isObject()) throw invalid();
    }

    private static ApiOperationException invalid() {
        return new ApiOperationException(ErrorCode.INVALID_REQUEST, "Request is invalid");
    }
}
