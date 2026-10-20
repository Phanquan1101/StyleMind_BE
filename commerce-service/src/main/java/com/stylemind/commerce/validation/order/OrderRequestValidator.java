package com.stylemind.commerce.validation.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.enums.CancellationStatus;
import com.stylemind.commerce.enums.OrderStatus;
import com.stylemind.commerce.enums.PaymentMethod;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class OrderRequestValidator {

    private static final Set<String> CHECKOUT_FIELDS = Set.of("checkout_key", "shipping_address_id", "payment_method");
    private static final Set<String> STATUS_FIELDS = Set.of("order_status", "reason");
    private static final Set<String> CANCEL_FIELDS = Set.of("reason");
    private static final Set<String> REVIEW_FIELDS = Set.of("request_status", "decision_reason");
    private static final Set<String> LIST_FIELDS = Set.of("status", "page", "size");
    private static final String KEY_PATTERN = "[A-Za-z0-9._:-]{8,128}";

    public CheckoutCommand checkout(JsonNode body) {
        requireFields(body, CHECKOUT_FIELDS);
        String key = text(body, "checkout_key", false);
        if (!key.matches(KEY_PATTERN)) {
            throw invalidRequest();
        }
        return new CheckoutCommand(key, uuid(text(body, "shipping_address_id", false)),
                enumValue(text(body, "payment_method", false), PaymentMethod.class));
    }

    public StatusCommand status(JsonNode body) {
        requireFields(body, STATUS_FIELDS);
        return new StatusCommand(enumValue(text(body, "order_status", false), OrderStatus.class),
                nullableBoundedText(body, "reason", 500));
    }

    public String cancellationReason(JsonNode body) {
        requireFields(body, CANCEL_FIELDS);
        return boundedText(text(body, "reason", false), 5, 500);
    }

    public CancellationReviewCommand review(JsonNode body) {
        requireFields(body, REVIEW_FIELDS);
        CancellationStatus status = enumValue(text(body, "request_status", false), CancellationStatus.class);
        if (status == CancellationStatus.PENDING) {
            throw invalidRequest();
        }
        String reason = nullableBoundedText(body, "decision_reason", 500);
        if (status == CancellationStatus.REJECTED && reason == null) {
            throw invalidRequest();
        }
        return new CancellationReviewCommand(status, reason);
    }

    public ListCriteria list(HttpServletRequest request) {
        Map<String, String[]> params = request.getParameterMap();
        if (!LIST_FIELDS.containsAll(params.keySet())) {
            throw invalidRequest();
        }
        params.forEach((key, values) -> {
            if (values == null || values.length != 1 || values[0] == null) {
                throw invalidRequest();
            }
        });
        OrderStatus status = params.containsKey("status")
                ? enumValue(params.get("status")[0].strip().toUpperCase(Locale.ROOT), OrderStatus.class) : null;
        int page = parseInt(params.get("page"), 0);
        int size = parseInt(params.get("size"), 20);
        if (page < 0 || size < 1 || size > 100) {
            throw invalidRequest();
        }
        return new ListCriteria(status, page, size);
    }

    public UUID pathId(String raw) {
        return uuid(raw);
    }

    public record CheckoutCommand(String checkoutKey, UUID shippingAddressId, PaymentMethod paymentMethod) {
    }

    public record StatusCommand(OrderStatus orderStatus, String reason) {
    }

    public record CancellationReviewCommand(CancellationStatus requestStatus, String decisionReason) {
    }

    public record ListCriteria(OrderStatus status, int page, int size) {
        public long offset() {
            return (long) page * size;
        }
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

    private int parseInt(String[] raw, int defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        if (raw[0].isEmpty() || !raw[0].chars().allMatch(Character::isDigit)) {
            throw invalidRequest();
        }
        try {
            return Integer.parseInt(raw[0]);
        } catch (NumberFormatException exception) {
            throw invalidRequest();
        }
    }

    private <E extends Enum<E>> E enumValue(String raw, Class<E> enumType) {
        try {
            return Enum.valueOf(enumType, raw);
        } catch (RuntimeException exception) {
            throw invalidRequest();
        }
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
