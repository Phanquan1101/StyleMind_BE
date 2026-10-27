package com.stylemind.commerce;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.stylemind.commerce.client.payment.PaymentAttemptResponse;
import com.stylemind.commerce.client.payment.PaymentClient;
import com.stylemind.commerce.dto.order.OrderResponse;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.service.order.OrderService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"management.health.db.enabled=false", "spring.flyway.enabled=false",
        "spring.datasource.hikari.initialization-fail-timeout=-1"})
@AutoConfigureMockMvc
class OrderPaymentRecoveryHttpTests {
    final UUID owner = UUID.randomUUID();
    final UUID other = UUID.randomUUID();
    final UUID order = UUID.randomUUID();
    @Autowired MockMvc mvc;
    @MockitoBean OrderService orders;
    @MockitoBean PaymentClient payment;
    @MockitoBean JwtDecoder decoder;

    @BeforeEach
    void configure() {
        when(decoder.decode("owner")).thenReturn(jwt("owner", owner, "CUSTOMER"));
        when(decoder.decode("admin")).thenReturn(jwt("admin", owner, "ADMIN"));
        when(decoder.decode("other")).thenReturn(jwt("other", other, "ADMIN"));
        when(decoder.decode("no-role")).thenReturn(jwt("no-role", owner, "OTHER"));
        when(orders.get(owner, false, order)).thenReturn(order("SEPAY_VIETQR", "PENDING_PAYMENT"));
        when(orders.get(other, false, order)).thenThrow(new ApiOperationException(HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND", "Order was not found"));
        when(payment.create(any())).thenReturn(new PaymentAttemptResponse(UUID.randomUUID(), order,
                "SEPAY_VIETQR", 125000, "SMTEST", "PENDING", Instant.now().plusSeconds(900), "SMTEST", "qr"));
    }

    @Test
    void customerAndAdminUseOwnCommittedSnapshotAndSameKeyForRecovery() throws Exception {
        for (String token : List.of("owner", "admin")) {
            mvc.perform(post(path()).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.qrPayload").value("qr"))
                    .andExpect(header().string("Cache-Control", "no-store"));
        }
        verify(payment, times(2)).create(argThat(r -> r.orderId().equals(order) && r.customerId().equals(owner)
                && r.requestKey().equals("initial-" + order) && r.acceptedTotalVnd() == 125000));
        verify(orders, times(2)).get(owner, false, order);
    }

    @Test
    void missingJwtRoleForeignAdminMissingOrderAndMalformedInputCannotSendPayment() throws Exception {
        mvc.perform(post(path())).andExpect(status().isUnauthorized());
        mvc.perform(post(path()).header("Authorization", "Bearer no-role")).andExpect(status().isForbidden());
        mvc.perform(post(path()).header("Authorization", "Bearer other")).andExpect(status().isNotFound());
        UUID missing = UUID.randomUUID();
        when(orders.get(owner, false, missing)).thenThrow(new ApiOperationException(HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND", "Order was not found"));
        mvc.perform(post("/api/v1/orders/" + missing + "/payment-attempt").header("Authorization", "Bearer owner"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(post("/api/v1/orders/invalid/payment-attempt").header("Authorization", "Bearer owner"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(path()).header("Authorization", "Bearer owner").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customer_id\":\"" + other + "\",\"accepted_total_vnd\":1}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(payment);
    }

    @Test
    void rejectsNonPayableOrderAndMapsSafeUpstreamFailure() throws Exception {
        when(orders.get(owner, false, order)).thenReturn(order("SEPAY_VIETQR", "CANCELLED"));
        mvc.perform(post(path()).header("Authorization", "Bearer owner")).andExpect(status().isConflict());
        verifyNoInteractions(payment);
        when(orders.get(owner, false, order)).thenReturn(order("SEPAY_VIETQR", "PENDING_PAYMENT"));
        when(payment.create(any())).thenThrow(new ApiOperationException(HttpStatus.SERVICE_UNAVAILABLE,
                "PAYMENT_DELIVERY_UNKNOWN", "Payment could not be confirmed"));
        mvc.perform(post(path()).header("Authorization", "Bearer owner"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("PAYMENT_DELIVERY_UNKNOWN"));
    }

    private String path() { return "/api/v1/orders/" + order + "/payment-attempt"; }
    private OrderResponse order(String method, String state) {
        return new OrderResponse(order, "ORDER-TEST", owner, "checkout-test", state, method, 125000,
                null, null, null, List.of(), List.of(), null);
    }
    private Jwt jwt(String value, UUID subject, String role) {
        return Jwt.withTokenValue(value).header("alg", "RS256").subject(subject.toString())
                .issuedAt(Instant.now().minusSeconds(1)).expiresAt(Instant.now().plusSeconds(300))
                .claim("roles", List.of(role)).build();
    }
}
