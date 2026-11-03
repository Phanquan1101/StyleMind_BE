package com.stylemind.commerce;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.stylemind.commerce.controller.cart.CartController;
import com.stylemind.commerce.dto.cart.CartResponse;
import com.stylemind.commerce.dto.cart.CartOwner;
import com.stylemind.commerce.errors.ApiExceptionHandler;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.service.cart.CartService;
import com.stylemind.commerce.validation.cart.CartRequestValidator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GuestCartHeaderTests {
    private static final String KEY_A = "guest-cart-test-owner-aaaa";
    private static final String KEY_B = "guest-cart-test-owner-bbbb";
    private final CartRequestValidator validator = new CartRequestValidator();
    private final CartService carts = mock(CartService.class);
    private MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new CartController(carts, validator))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        when(carts.getCart(any())).thenAnswer(call -> {
            CartOwner owner = call.getArgument(0);
            return new CartResponse(null, null, owner.guestSessionKey(), "ACTIVE", List.of(), 0);
        });
        when(carts.clear(any())).thenReturn(new CartResponse(null, null, KEY_A, "ACTIVE", List.of(), 0));
    }

    @Test void responseToStringDoesNotRevealCredential() {
        assertThat(new CartResponse(null, null, KEY_A, "ACTIVE", List.of(), 0).toString())
                .contains("[REDACTED]").doesNotContain(KEY_A);
    }

    @Test void readsGuestCartUsingHeaderWithoutQueryOrJwt() throws Exception {
        mvc.perform(get("/api/v1/cart").header("X-Guest-Session-Key", KEY_A)).andExpect(status().isOk());
        verify(carts).getCart(CartOwner.guest(KEY_A));
    }

    @Test void deleteAndClearUseTheSameHeaderOwner() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(delete("/api/v1/cart/items/" + id).header("X-Guest-Session-Key", KEY_A))
                .andExpect(status().isNoContent());
        verify(carts).removeItem(CartOwner.guest(KEY_A), id);
        mvc.perform(delete("/api/v1/cart/items").header("X-Guest-Session-Key", KEY_A)).andExpect(status().isOk());
        verify(carts).clear(CartOwner.guest(KEY_A));
    }

    @Test void rejectsMissingMalformedAndAmbiguousCredentialsBeforeService() throws Exception {
        mvc.perform(get("/api/v1/cart")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/cart").header("X-Guest-Session-Key", "bad")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/cart").header("X-Guest-Session-Key", KEY_A)
                .queryParam("guest_session_key", KEY_B)).andExpect(status().isBadRequest());
        verifyNoInteractions(carts);
    }

    @Test void retainsLegacyQueryAndRejectsUnrelatedQueryParameters() throws Exception {
        mvc.perform(get("/api/v1/cart").queryParam("guest_session_key", KEY_A)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/cart").header("X-Guest-Session-Key", KEY_A).queryParam("customer_id", "anything"))
                .andExpect(status().isBadRequest());
        verify(carts).getCart(CartOwner.guest(KEY_A));
    }

    @Test void separateHeadersResolveSeparateOwnersAndCannotDeleteForeignItems() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ApiOperationException(org.springframework.http.HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Not found"))
                .when(carts).removeItem(CartOwner.guest(KEY_B), id);
        mvc.perform(get("/api/v1/cart").header("X-Guest-Session-Key", KEY_A)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/cart").header("X-Guest-Session-Key", KEY_B)).andExpect(status().isOk());
        verify(carts).getCart(CartOwner.guest(KEY_A));
        verify(carts).getCart(CartOwner.guest(KEY_B));
        mvc.perform(delete("/api/v1/cart/items/" + id).header("X-Guest-Session-Key", KEY_B))
                .andExpect(status().isNotFound());
    }

    @Test void authenticatedOwnerStillTakesPrecedence() {
        UUID customer = UUID.randomUUID();
        var request = new MockHttpServletRequest();
        request.addHeader("X-Guest-Session-Key", KEY_A);
        request.setParameter("guest_session_key", KEY_B);
        assertThat(validator.ownerFromQuery(customer, request)).isEqualTo(CartOwner.customer(customer));
    }
}
