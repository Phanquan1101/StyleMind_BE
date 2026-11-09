package com.stylemind.commerce.validation.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stylemind.commerce.dto.order.CheckoutSelection;
import com.stylemind.commerce.errors.ApiOperationException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CheckoutSelectionValidator {
    public String recoveryKey(JsonNode body) {
        if (body == null || !body.isObject() || body.size() != 1 || !body.has("checkout_key")
                || !body.get("checkout_key").isTextual() || !body.get("checkout_key").textValue().matches("[A-Za-z0-9._:-]{8,128}")) throw invalid();
        return body.get("checkout_key").textValue();
    }
    public CheckoutSelection selection(JsonNode body, boolean buyNow, boolean checkout) {
        return selection(body, buyNow, checkout, false);
    }
    public CheckoutSelection selection(JsonNode body, boolean buyNow, boolean checkout, boolean guest) {
        Set<String> expected = new HashSet<>(buyNow ? Set.of("variant_id", "quantity") : Set.of("cart_item_ids"));
        if (checkout) expected.addAll(guest ? Set.of("checkout_key", "shipping_address", "payment_method") : Set.of("checkout_key", "shipping_address_id", "payment_method"));
        if (body == null || !body.isObject() || body.size() != expected.size()) throw invalid();
        body.fieldNames().forEachRemaining(name -> { if (!expected.contains(name)) throw invalid(); });
        if (buyNow) {
            JsonNode quantity = body.get("quantity");
            if (quantity == null || !quantity.isIntegralNumber() || !quantity.canConvertToInt()
                    || quantity.intValue() < 1 || quantity.intValue() > 999) throw invalid();
            return new CheckoutSelection(List.of(), id(body.get("variant_id")), quantity.intValue());
        }
        JsonNode ids = body.get("cart_item_ids");
        if (ids == null || !ids.isArray() || ids.isEmpty() || ids.size() > 100) throw invalid();
        List<UUID> values = new ArrayList<>();
        ids.forEach(value -> values.add(id(value)));
        if (new HashSet<>(values).size() != values.size()) throw invalid();
        return new CheckoutSelection(List.copyOf(values), null, 0);
    }

    public JsonNode accountCheckoutFields(JsonNode body) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        for (String field : List.of("checkout_key", "shipping_address_id", "payment_method")) result.set(field, body.get(field));
        return result;
    }

    public JsonNode guestCheckoutFields(JsonNode body) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        for (String field : List.of("checkout_key", "shipping_address", "payment_method")) result.set(field, body.get(field));
        return result;
    }

    private UUID id(JsonNode value) {
        if (value == null || !value.isTextual()) throw invalid();
        try { return UUID.fromString(value.textValue()); }
        catch (IllegalArgumentException exception) { throw invalid(); }
    }
    private ApiOperationException invalid() {
        return new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request is missing or malformed");
    }
}
