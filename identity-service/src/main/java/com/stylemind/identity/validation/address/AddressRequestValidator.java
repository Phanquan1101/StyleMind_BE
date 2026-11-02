package com.stylemind.identity.validation.address;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.identity.directory.VietnamAdministrativeDirectory;
import com.stylemind.identity.errors.ApiOperationException;
import com.stylemind.identity.model.address.AddressPatch;
import com.stylemind.identity.model.address.AddressValues;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.springframework.http.HttpStatus;
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

    public AddressValues validateCreate(JsonNode body) {
        requireObject(body);
        checkFields(body);
        String label = optionalNullableText(body, "label", 50, false);
        String name = requiredText(body, "recipient_name", 100);
        String phone = phoneValidator.canonicalize(requiredTextRaw(body, "recipient_phone", 64));
        String line = requiredText(body, "address_line", 500);
        String ward = optionalNullableText(body, "ward_code", 16, true);
        return new AddressValues(label, name, phone, line, ward);
    }

    public AddressPatch validatePatch(JsonNode body) {
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
        String label = hasLabel ? optionalNullableText(body, "label", 50, false) : null;
        String name = hasName ? requiredText(body, "recipient_name", 100) : null;
        String phone = hasPhone ? phoneValidator.canonicalize(requiredTextRaw(body, "recipient_phone", 64)) : null;
        String line = hasLine ? requiredText(body, "address_line", 500) : null;
        String ward = hasWard ? optionalNullableText(body, "ward_code", 16, true) : null;
        return new AddressPatch(hasLabel, label, hasName, name, hasPhone, phone, hasLine, line, hasWard, ward);
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
        return new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request is invalid", 0);
    }
}
