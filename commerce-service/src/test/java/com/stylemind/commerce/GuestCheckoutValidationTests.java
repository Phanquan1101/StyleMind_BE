package com.stylemind.commerce;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.security.GuestOrderAccess;
import com.stylemind.commerce.validation.order.GuestCheckoutValidator;
import org.junit.jupiter.api.Test;

class GuestCheckoutValidationTests {
    final ObjectMapper json = new ObjectMapper();
    final GuestCheckoutValidator validator = new GuestCheckoutValidator();

    @Test void acceptsBothMethodsAndFreeFormPhoneWithUnicodeTrimming() throws Exception {
        for (String method : java.util.List.of("COD", "SEPAY_VIETQR")) {
            var body = body(); body.put("payment_method", method);
            ((ObjectNode)body.get("shipping_address")).put("recipient_phone", "\u00a0 phone +1 (23) \u3000");
            var command = validator.checkout(body);
            assertThat(command.address().recipientPhone()).isEqualTo("phone +1 (23)");
            assertThat(command.address().sourceAddressId()).isNull();
        }
    }

    @Test void rejectsUnknownMissingNullWrongTypeBlankControlAndOverlongFields() throws Exception {
        var bodies = new java.util.ArrayList<ObjectNode>();
        var b=body(); b.put("customer_id","injected"); bodies.add(b);
        b=body(); b.remove("shipping_address"); bodies.add(b);
        b=body(); b.putNull("payment_method"); bodies.add(b);
        b=body(); b.put("payment_method","CASH"); bodies.add(b);
        b=body(); b.put("checkout_key","short"); bodies.add(b);
        for (String field:java.util.List.of("recipient_name","recipient_phone","address_line")) {
            for (String value:java.util.List.of(" ","a\nb","x".repeat(501))) {
                b=body(); ((ObjectNode)b.get("shipping_address")).put(field,value); bodies.add(b);
            }
            b=body(); ((ObjectNode)b.get("shipping_address")).putNull(field); bodies.add(b);
            b=body(); ((ObjectNode)b.get("shipping_address")).put(field,123); bodies.add(b);
        }
        b=body(); ((ObjectNode)b.get("shipping_address")).put("source_address_id","injected"); bodies.add(b);
        for (var invalid:bodies) assertThatThrownBy(() -> validator.checkout(invalid)).isInstanceOf(ApiOperationException.class);
    }

    @Test void guestCredentialsAreStrictAndHashesAreStableAndDistinct() {
        for (String value:java.util.Arrays.asList(null,"short","guest-session with spaces","x".repeat(129)))
            assertThatThrownBy(() -> GuestOrderAccess.requireKey(value)).isInstanceOf(ApiOperationException.class);
        String key="guest-session-test-0001";
        assertThat(GuestOrderAccess.digest(key)).hasSize(64).isEqualTo(GuestOrderAccess.digest(key))
                .isNotEqualTo(GuestOrderAccess.digest("guest-session-test-0002"));
    }

    private ObjectNode body() throws Exception {
        return (ObjectNode)json.readTree("""
            {"checkout_key":"checkout-key-0001","payment_method":"COD","shipping_address":
            {"recipient_name":"Guest Buyer","recipient_phone":"anything 123","address_line":"1 Main St, Hanoi"}}
            """);
    }
}
