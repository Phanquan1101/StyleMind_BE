package com.stylemind.commerce.validation.returns;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.commerce.dto.returns.RefundSource;
import com.stylemind.commerce.dto.returns.ReturnCommand;
import com.stylemind.commerce.dto.returns.ReturnCommand.Action;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.validation.order.OrderRequestValidator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReturnRequestValidator {

    private final ObjectMapper objectMapper;
    private final OrderRequestValidator orderValidator;

    public ReturnCommand create(byte[] body) {
        JsonNode request;
        try {
            request = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY,
                            DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(new ByteArrayInputStream(body));
        } catch (IOException exception) {
            throw invalidRequest();
        }
        requireKnownFields(request, Set.of("request_key", "reason", "items"));
        UUID requestKey = pathId(text(request, "request_key"));
        String reason = reason(request);
        JsonNode items = request.path("items");
        if (reason == null || !items.isArray() || items.isEmpty()) {
            throw invalidRequest();
        }

        Map<UUID, Integer> quantities = new TreeMap<>();
        for (JsonNode item : items) {
            requireKnownFields(item, Set.of("order_item_id", "quantity"));
            UUID itemId = pathId(text(item, "order_item_id"));
            JsonNode quantity = item.path("quantity");
            if (!quantity.isIntegralNumber() || !quantity.canConvertToInt() || quantity.intValue() < 1) {
                throw invalidRequest();
            }
            try {
                quantities.merge(itemId, quantity.intValue(), Math::addExact);
            } catch (ArithmeticException exception) {
                throw invalidRequest();
            }
        }
        return new ReturnCommand(requestKey, reason, quantities);
    }

    public ReturnCommand.Decision decision(JsonNode body) {
        requireKnownFields(body, Set.of("action", "reason"));
        Action action = enumValue(text(body, "action"), Action.class);
        String reason = reason(body);
        if (action.requiresReason() && reason == null) {
            throw invalidRequest();
        }
        return new ReturnCommand.Decision(action, reason);
    }

    public RefundSource.Result refundResult(JsonNode body) {
        requireKnownFields(body, Set.of("refund_id", "source_type", "source_id", "order_id",
                "amount_vnd", "refunded_at"));
        JsonNode amount = body.path("amount_vnd");
        if (!amount.isIntegralNumber() || !amount.canConvertToLong() || amount.longValue() < 1) {
            throw invalidRequest();
        }
        OffsetDateTime refundedAt;
        try {
            refundedAt = OffsetDateTime.parse(text(body, "refunded_at"));
        } catch (DateTimeParseException exception) {
            throw invalidRequest();
        }
        return new RefundSource.Result(pathId(text(body, "refund_id")), sourceType(text(body, "source_type")),
                pathId(text(body, "source_id")), pathId(text(body, "order_id")), amount.longValue(), refundedAt);
    }

    public UUID pathId(String raw) {
        return orderValidator.pathId(raw);
    }

    public RefundSource.Type sourceType(String raw) {
        return enumValue(raw, RefundSource.Type.class);
    }

    public PageCriteria pagination(Map<String, String[]> params) {
        if (!Set.of("page", "size").containsAll(params.keySet())) {
            throw invalidRequest();
        }
        int page = integer(params.get("page"), 0);
        int size = integer(params.get("size"), 20);
        if (page < 0 || size < 1 || size > 100) {
            throw invalidRequest();
        }
        return new PageCriteria(page, size);
    }

    private int integer(String[] values, int fallback) {
        if (values == null) {
            return fallback;
        }
        if (values.length != 1 || !values[0].matches("[0-9]+")) {
            throw invalidRequest();
        }
        try {
            return Integer.parseInt(values[0]);
        } catch (NumberFormatException exception) {
            throw invalidRequest();
        }
    }

    private String reason(JsonNode body) {
        JsonNode node = body.path("reason");
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = text(body, "reason").strip();
        if (value.codePointCount(0, value.length()) > 1000
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw invalidRequest();
        }
        return value.isEmpty() ? null : value;
    }

    private static <E extends Enum<E>> E enumValue(String value, Class<E> type) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw invalidRequest();
        }
    }

    private String text(JsonNode body, String field) {
        JsonNode node = body.path(field);
        if (!node.isTextual()) {
            throw invalidRequest();
        }
        return node.textValue();
    }

    private void requireKnownFields(JsonNode body, Set<String> allowed) {
        if (body == null || !body.isObject()) {
            throw invalidRequest();
        }
        var fields = body.fieldNames();
        while (fields.hasNext()) {
            if (!allowed.contains(fields.next())) {
                throw invalidRequest();
            }
        }
    }

    private static ApiOperationException invalidRequest() {
        return new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "Return request is missing or malformed");
    }

    public record PageCriteria(int page, int size) {
        public long offset() {
            return (long) page * size;
        }
    }
}
