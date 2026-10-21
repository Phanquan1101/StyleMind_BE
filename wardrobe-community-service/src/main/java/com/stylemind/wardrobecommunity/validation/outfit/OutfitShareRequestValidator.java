package com.stylemind.wardrobecommunity.validation.outfit;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class OutfitShareRequestValidator {

    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MAX_LOCAL_PART_LENGTH = 64;
    /**
     * Dot-atom local part, and a domain of at least two letter/digit/hyphen labels. This is close to, and slightly
     * stricter than, the Jakarta @Email rule Identity applies when an account registers.
     */
    private static final Pattern EMAIL = Pattern.compile(
            "[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
                    + "@[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+");

    /** The only accepted body is {"email": "..."}; the address is returned trimmed and lowercase. */
    public String email(JsonNode body) {
        if (body == null || !body.isObject() || body.size() != 1) {
            throw invalidRequest();
        }
        for (Iterator<String> names = body.fieldNames(); names.hasNext();) {
            if (!"email".equals(names.next())) {
                throw invalidRequest();
            }
        }
        JsonNode node = body.get("email");
        if (node == null || !node.isTextual()) {
            throw invalidRequest();
        }
        String email = node.textValue().strip().toLowerCase(Locale.ROOT);
        if (email.isEmpty() || email.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(email).matches()
                || email.indexOf('@') > MAX_LOCAL_PART_LENGTH) {
            throw invalidRequest();
        }
        return email;
    }

    private ApiOperationException invalidRequest() {
        return new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "Request is missing or malformed");
    }
}
