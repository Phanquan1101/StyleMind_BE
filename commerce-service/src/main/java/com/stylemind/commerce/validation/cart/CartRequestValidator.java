package com.stylemind.commerce.validation.cart;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.model.cart.CartItemCommand;
import com.stylemind.commerce.model.cart.CartOwner;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CartRequestValidator {

    private static final Set<String> ITEM_FIELDS = Set.of("variant_id", "quantity", "guest_session_key");
    private static final Set<String> UPDATE_FIELDS = Set.of("quantity", "guest_session_key");
    private static final Set<String> MERGE_FIELDS = Set.of("guest_session_key");
    private static final String GUEST_PATTERN = "[A-Za-z0-9._:-]{16,128}";

    public CartOwner owner(UUID customerId, String guestSessionKey) {
        if (customerId != null) {
            return CartOwner.customer(customerId);
        }
        String key = guestKey(guestSessionKey);
        return CartOwner.guest(key);
    }

    public CartOwner ownerFromQuery(UUID customerId, HttpServletRequest request) {
        if (!request.getParameterMap().keySet().stream().allMatch("guest_session_key"::equals)) {
            throw invalidRequest();
        }
        return owner(customerId, request.getParameter("guest_session_key"));
    }

    public CartItemCommand itemCommand(JsonNode body) {
        requireFields(body, ITEM_FIELDS);
        return new CartItemCommand(uuid(text(body, "variant_id", false)), quantity(body.get("quantity")),
                optionalGuestKey(body));
    }

    public CartItemCommand updateCommand(JsonNode body) {
        requireFields(body, UPDATE_FIELDS);
        return new CartItemCommand(null, quantity(body.get("quantity")), optionalGuestKey(body));
    }

    public String mergeGuestKey(JsonNode body) {
        requireFields(body, MERGE_FIELDS);
        return optionalGuestKey(body);
    }

    public UUID pathId(String raw) {
        return uuid(raw);
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

    private int quantity(JsonNode node) {
        if (node == null || !node.canConvertToInt() || node.intValue() < 1 || node.intValue() > 999) {
            throw invalidRequest();
        }
        return node.intValue();
    }

    private String optionalGuestKey(JsonNode body) {
        JsonNode node = body.get("guest_session_key");
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw invalidRequest();
        }
        return guestKey(node.textValue());
    }

    private String guestKey(String raw) {
        if (raw == null || !raw.matches(GUEST_PATTERN)) {
            throw invalidRequest();
        }
        return raw;
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

    private UUID uuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (RuntimeException exception) {
            throw invalidRequest();
        }
    }

    private ApiOperationException invalidRequest() {
        return new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "Request is missing or malformed");
    }
}
