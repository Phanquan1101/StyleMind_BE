package com.stylemind.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.validation.cart.CartRequestValidator;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CartRequestValidatorTests {

    private final ObjectMapper mapper = new ObjectMapper();
    private final CartRequestValidator validator = new CartRequestValidator();

    @Test
    void updateAcceptsVariantAndKeepsTheLegacyQuantityOnlyContract() throws Exception {
        UUID variantId = UUID.randomUUID();
        var update = validator.updateCommand(mapper.readTree("""
                {"variant_id":"%s","quantity":3,"guest_session_key":null}
                """.formatted(variantId)));
        assertThat(update.variantId()).isEqualTo(variantId);
        assertThat(update.quantity()).isEqualTo(3);

        var legacy = validator.updateCommand(mapper.readTree("""
                {"quantity":2,"guest_session_key":"guest-session-0001"}
                """));
        assertThat(legacy.variantId()).isNull();
        assertThat(legacy.quantity()).isEqualTo(2);
    }

    @Test
    void updateRejectsMalformedOrUnexpectedVariantFields() throws Exception {
        assertThatThrownBy(() -> validator.updateCommand(mapper.readTree("""
                {"variant_id":"not-a-uuid","quantity":1,"guest_session_key":null}
                """))).isInstanceOf(ApiOperationException.class);
        assertThatThrownBy(() -> validator.updateCommand(mapper.readTree("""
                {"variant_id":null,"quantity":1,"guest_session_key":null}
                """))).isInstanceOf(ApiOperationException.class);
        assertThatThrownBy(() -> validator.updateCommand(mapper.readTree("""
                {"variant_id":"00000000-0000-0000-0000-000000000001","quantity":1,"guest_session_key":null,"extra":true}
                """))).isInstanceOf(ApiOperationException.class);
    }
}
