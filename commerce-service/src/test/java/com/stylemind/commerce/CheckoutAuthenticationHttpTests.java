package com.stylemind.commerce;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.stylemind.commerce.dto.cart.CartResponse;
import com.stylemind.commerce.service.cart.CartService;
import com.stylemind.commerce.service.order.OrderService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"spring.flyway.enabled=false", "management.health.db.enabled=false",
        "spring.datasource.hikari.initialization-fail-timeout=-1"})
@AutoConfigureMockMvc
class CheckoutAuthenticationHttpTests {
    @Autowired MockMvc mvc;
    @MockitoBean OrderService orders;
    @MockitoBean CartService carts;
    @MockitoBean JwtDecoder decoder;
    private static final String KEY = "existing-guest-cart-0001";

    @Test void guestSessionCannotCreateOrdersOrNewQrCheckouts() throws Exception {
        for (String path : List.of("/api/v1/guest/orders", "/api/v1/guest/orders/selected", "/api/v1/guest/orders/buy-now",
                "/api/v1/guest/qr-checkouts/selected", "/api/v1/guest/qr-checkouts/buy-now")) {
            mvc.perform(post(path).header("X-Guest-Session-Key", KEY).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
        var jwt = new Jwt("test-token", Instant.now().minusSeconds(1), Instant.now().plusSeconds(300),
                Map.of("alg", "RS256"), Map.of("sub", UUID.randomUUID().toString(), "roles", List.of("CUSTOMER")));
        when(decoder.decode("test-token")).thenReturn(jwt);
        mvc.perform(post("/api/v1/guest/orders").header("X-Guest-Session-Key", KEY)
                .header("Authorization", "Bearer test-token").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(orders);
    }

    @Test void guestCheckoutRoutesRequireGuestCredential() throws Exception {
        String id = UUID.randomUUID().toString();
        mvc.perform(get("/api/v1/guest/checkout/prices")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/guest/orders").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/guest/orders/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/guest/orders/" + id + "/payment-attempt"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(orders);
    }

    @Test void invalidGuestBodyAndUnexpectedRecoveryBodyFailBeforeServiceCalls() throws Exception {
        mvc.perform(post("/api/v1/guest/orders").header("X-Guest-Session-Key",KEY)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/guest/orders/"+UUID.randomUUID()+"/payment-attempt")
                .header("X-Guest-Session-Key",KEY).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/guest/checkout/prices").header("X-Guest-Session-Key","bad"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(orders);
    }

    @Test void registeredCheckoutStillRequiresLoginEvenWithGuestCartKey() throws Exception {
        mvc.perform(get("/api/v1/checkout/prices").header("X-Guest-Session-Key",KEY)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/orders").header("X-Guest-Session-Key",KEY).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(orders);
    }

    @Test void existingGuestCartRemainsPublic() throws Exception {
        when(carts.getCart(any())).thenReturn(new CartResponse(null, null, KEY, "ACTIVE", List.of(), 0));
        mvc.perform(get("/api/v1/cart").queryParam("guest_session_key", KEY)).andExpect(status().isOk())
                .andExpect(jsonPath("$.guest_session_key").value(KEY));
        verify(carts).getCart(argThat(owner -> owner.customerId() == null && KEY.equals(owner.guestSessionKey())));
    }

    @Test void jwtDoesNotReplaceGuestOwnershipCredential() throws Exception {
        var jwt = new Jwt("test-token", Instant.now().minusSeconds(1), Instant.now().plusSeconds(300),
                Map.of("alg", "RS256"), Map.of("sub", UUID.randomUUID().toString(), "roles", List.of("CUSTOMER")));
        when(decoder.decode("test-token")).thenReturn(jwt);
        mvc.perform(get("/api/v1/guest/orders/" + UUID.randomUUID()).header("Authorization", "Bearer test-token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/guest/checkout/prices").header("Authorization", "Bearer test-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(orders);
    }

    @Test void openApiAdvertisesGuestCheckout() throws Exception {
        String api = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(api).contains("/api/v1/guest/orders", "/api/v1/guest/checkout/prices", "/api/v1/cart", "/api/v1/orders");
    }
}
