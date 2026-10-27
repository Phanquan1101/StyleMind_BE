package com.stylemind.payment.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.payment.client.CommerceOrderPaymentSnapshot;
import com.stylemind.payment.errors.PaymentApiException;
import com.stylemind.payment.model.PaymentMethod;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public record OrderPaymentRequest(CommerceOrderPaymentSnapshot order, String requestKey) {
    private static final Set<String> FIELDS = Set.of("order_id", "customer_id", "payment_method",
            "accepted_total_vnd", "order_status", "request_key");

    public static OrderPaymentRequest parse(JsonNode body) {
        try {
            if (body == null || !body.isObject() || body.size() != FIELDS.size()) {
                throw new IllegalArgumentException();
            }
            for (String field : FIELDS) {
                if (!body.hasNonNull(field) || (!field.equals("accepted_total_vnd") && !body.get(field).isTextual())) {
                    throw new IllegalArgumentException();
                }
            }
            JsonNode amount = body.get("accepted_total_vnd");
            if (!amount.isIntegralNumber() || !amount.canConvertToLong() || amount.longValue() <= 0) {
                throw new IllegalArgumentException();
            }
            UUID orderId = uuid(body.get("order_id").textValue());
            UUID customerId = uuid(body.get("customer_id").textValue());
            PaymentMethod method = PaymentMethod.valueOf(body.get("payment_method").textValue());
            String status = body.get("order_status").textValue();
            String key = body.get("request_key").textValue();
            if (!key.equals("initial-" + orderId)
                    || !(method == PaymentMethod.COD && status.equals("PLACED")
                    || method == PaymentMethod.SEPAY_VIETQR && status.equals("PENDING_PAYMENT"))) {
                throw new IllegalArgumentException();
            }
            return new OrderPaymentRequest(new CommerceOrderPaymentSnapshot(orderId, customerId, method,
                    amount.longValue(), status), key);
        } catch (IllegalArgumentException exception) {
            throw new PaymentApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Order payment snapshot is invalid");
        }
    }

    private static UUID uuid(String value) {
        UUID id = UUID.fromString(value);
        if (!id.toString().equalsIgnoreCase(value)) {
            throw new IllegalArgumentException();
        }
        return id;
    }
}
