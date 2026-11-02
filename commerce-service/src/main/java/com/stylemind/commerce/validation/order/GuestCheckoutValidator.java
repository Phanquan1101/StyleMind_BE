package com.stylemind.commerce.validation.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.commerce.dto.order.AddressSnapshot;
import com.stylemind.commerce.enums.PaymentMethod;
import com.stylemind.commerce.errors.ApiOperationException;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class GuestCheckoutValidator {
    public record CheckoutCommand(String checkoutKey, PaymentMethod paymentMethod, AddressSnapshot address) {}

    public CheckoutCommand checkout(JsonNode body) {
        fields(body, Set.of("checkout_key", "payment_method", "shipping_address"));
        String key = text(body, "checkout_key");
        if (!key.matches("[A-Za-z0-9._:-]{8,128}")) throw invalid();
        PaymentMethod method;
        try { method = PaymentMethod.valueOf(text(body, "payment_method")); }
        catch (IllegalArgumentException exception) { throw invalid(); }
        JsonNode address = body.get("shipping_address");
        fields(address, Set.of("recipient_name", "recipient_phone", "address_line"));
        return new CheckoutCommand(key, method, new AddressSnapshot(null,
                bounded(address, "recipient_name", 100), bounded(address, "recipient_phone", 64),
                bounded(address, "address_line", 500)));
    }

    private void fields(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isObject() || node.size() != allowed.size()) throw invalid();
        node.fieldNames().forEachRemaining(name -> { if (!allowed.contains(name)) throw invalid(); });
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) throw invalid();
        return value.textValue();
    }

    private String bounded(JsonNode node, String field, int max) {
        String value = text(node, field).replaceAll("^[\\p{javaWhitespace}\\p{Zs}]+|[\\p{javaWhitespace}\\p{Zs}]+$", "");
        int length = value.codePointCount(0, value.length());
        if (length == 0 || length > max || value.codePoints().anyMatch(Character::isISOControl)) throw invalid();
        return value;
    }

    private ApiOperationException invalid() {
        return new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request is missing or malformed");
    }
}
