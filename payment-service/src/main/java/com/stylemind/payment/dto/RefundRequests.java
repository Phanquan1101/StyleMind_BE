package com.stylemind.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.payment.errors.RefundErrorCode;

public final class RefundRequests {

    private RefundRequests() { }

    public record Destination(
            @JsonProperty("bank_name") String bankName,
            @JsonProperty("account_number") String accountNumber,
            @JsonProperty("account_holder") String accountHolder) {

        public static Destination parse(JsonNode body) {
            requireObject(body, 3);
            return new Destination(requiredText(body, "bank_name", 100), requiredText(body, "account_number", 64),
                    requiredText(body, "account_holder", 150));
        }

        @Override
        public String toString() {
            return "Refund destination [redacted]";
        }
    }

    public record Completion(String transferReference, String proofReference) {
        public static Completion parse(JsonNode body) {
            requireObject(body, 2);
            return new Completion(requiredText(body, "transfer_reference", 160),
                    requiredText(body, "proof_reference", 160));
        }
    }

    public record Failure(String reason) {
        public static Failure parse(JsonNode body) {
            requireObject(body, 1);
            return new Failure(requiredText(body, "reason", 500));
        }
    }

    private static void requireObject(JsonNode body, int fields) {
        if (body == null || !body.isObject() || body.size() != fields) {
            throw RefundErrorCode.INVALID_REQUEST.exception();
        }
    }

    private static String requiredText(JsonNode body, String field, int maxLength) {
        JsonNode node = body.path(field);
        if (!node.isTextual()) {
            throw RefundErrorCode.INVALID_REQUEST.exception();
        }
        String value = node.textValue().strip();
        if (value.isBlank() || value.length() > maxLength || value.codePoints().anyMatch(Character::isISOControl)) {
            throw RefundErrorCode.INVALID_REQUEST.exception();
        }
        return value;
    }
}
