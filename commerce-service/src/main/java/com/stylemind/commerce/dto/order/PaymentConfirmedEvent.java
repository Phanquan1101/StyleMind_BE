package com.stylemind.commerce.dto.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.commerce.errors.ApiOperationException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public record PaymentConfirmedEvent(UUID event_id, String event_type, UUID payment_attempt_id,
        UUID order_id, UUID customer_id, String payment_method, String payment_reference,
        long amount_vnd, Instant confirmed_at) {
    private static final Set<String> FIELDS = Set.of("event_id", "event_type", "payment_attempt_id", "order_id",
            "customer_id", "payment_method", "payment_reference", "amount_vnd", "confirmed_at");

    public static PaymentConfirmedEvent parse(JsonNode node) {
        try {
            if (!node.isObject() || node.size() != FIELDS.size()) throw invalid();
            node.fieldNames().forEachRemaining(key -> { if (!FIELDS.contains(key)) throw invalid(); });
            if (!"PaymentConfirmed".equals(text(node, "event_type"))
                    || !Set.of("SEPAY_VIETQR", "COD").contains(text(node, "payment_method"))) throw invalid();
            var amount = node.path("amount_vnd");
            if (!amount.isIntegralNumber() || !amount.canConvertToLong() || amount.longValue() <= 0) throw invalid();
            String reference = text(node, "payment_reference");
            if (reference.isBlank() || reference.length() > 100 || reference.codePoints().anyMatch(Character::isISOControl))
                throw invalid();
            return new PaymentConfirmedEvent(uuid(node, "event_id"), "PaymentConfirmed", uuid(node, "payment_attempt_id"),
                    uuid(node, "order_id"), uuid(node, "customer_id"), text(node, "payment_method"), reference,
                    amount.longValue(), Instant.parse(text(node, "confirmed_at")));
        } catch (IllegalArgumentException | java.time.DateTimeException failure) {
            throw invalid();
        }
    }
    private static String text(JsonNode node, String key) {
        if (!node.path(key).isTextual()) throw invalid();
        return node.path(key).textValue();
    }
    private static UUID uuid(JsonNode node, String key) {
        String value = text(node, key);
        UUID id = UUID.fromString(value);
        if (!id.toString().equalsIgnoreCase(value)) throw invalid();
        return id;
    }
    private static ApiOperationException invalid() {
        return new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Payment event is malformed");
    }
}
