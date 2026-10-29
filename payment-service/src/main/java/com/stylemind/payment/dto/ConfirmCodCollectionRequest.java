package com.stylemind.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.payment.errors.PaymentApiException;
import java.time.Instant;
import java.util.UUID;

public record ConfirmCodCollectionRequest(
        @JsonProperty("payment_attempt_id") UUID paymentAttemptId,
        @JsonProperty("collected_amount_vnd") Long collectedAmountVnd,
        @JsonProperty("collection_reference") String collectionReference,
        @JsonProperty("collected_at") Instant collectedAt) {

    public static ConfirmCodCollectionRequest parse(JsonNode body) {
        try {
            if (body == null || !body.isObject()) throw new IllegalArgumentException();
            var allowed = java.util.Set.of("payment_attempt_id", "collected_amount_vnd", "collection_reference", "collected_at");
            body.fieldNames().forEachRemaining(key -> { if (!allowed.contains(key)) throw new IllegalArgumentException(); });
            var amount = body.path("collected_amount_vnd");
            if (!amount.isIntegralNumber() || !amount.canConvertToLong() || amount.longValue() <= 0
                    || !body.path("payment_attempt_id").isTextual()) throw new IllegalArgumentException();
            String id = body.path("payment_attempt_id").textValue();
            UUID attempt = UUID.fromString(id);
            if (!attempt.toString().equalsIgnoreCase(id)) throw new IllegalArgumentException();
            String reference = optionalText(body, "collection_reference");
            if (reference != null && (reference.length() > 255 || reference.codePoints().anyMatch(Character::isISOControl)))
                throw new IllegalArgumentException();
            String collected = optionalText(body, "collected_at");
            return new ConfirmCodCollectionRequest(attempt, amount.longValue(), reference,
                    collected == null ? null : Instant.parse(collected));
        } catch (IllegalArgumentException | java.time.DateTimeException failure) {
            throw new PaymentApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "COD collection request is invalid");
        }
    }

    private static String optionalText(JsonNode body, String name) {
        JsonNode value = body.get(name);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw new IllegalArgumentException();
        return value.textValue();
    }
}
