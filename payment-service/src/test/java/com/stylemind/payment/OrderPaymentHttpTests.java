package com.stylemind.payment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.stylemind.payment.dto.PaymentAttemptResponse;
import com.stylemind.payment.service.PaymentAttemptService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"management.health.db.enabled=false", "spring.flyway.enabled=false",
        "spring.datasource.hikari.initialization-fail-timeout=-1", "stylemind.payment.handoff-token=test-only-handoff"})
@AutoConfigureMockMvc
class OrderPaymentHttpTests {
    static final String PATH = "/internal/v1/payments/order-attempts";
    static final UUID ORDER = UUID.randomUUID();
    static final UUID CUSTOMER = UUID.randomUUID();
    @Autowired MockMvc mvc;
    @MockitoBean PaymentAttemptService payments;

    static String body(String method, String state) {
        return """
                {"order_id":"%s","customer_id":"%s","payment_method":"%s",
                 "accepted_total_vnd":125000,"order_status":"%s","request_key":"initial-%s"}
                """.formatted(ORDER, CUSTOMER, method, state, ORDER);
    }

    @Test
    void rejectsMissingWrongAndBearerOnlyCredentialsWithoutCallingPayments() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body("COD", "PLACED")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).header("X-StyleMind-Service-Token", "wrong")
                .contentType(MediaType.APPLICATION_JSON).content(body("COD", "PLACED")))
                .andExpect(status().isUnauthorized()).andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("test-only-handoff"))));
        mvc.perform(post(PATH).header("Authorization", "Bearer cannot-authorize-internal")
                .contentType(MediaType.APPLICATION_JSON).content(body("COD", "PLACED")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(payments);
    }

    @Test
    void authenticatedSnapshotReturnsExistingResponseShapeAndCreationLocation() throws Exception {
        UUID attempt = UUID.randomUUID();
        when(payments.createOrderAttempt(any(), any())).thenReturn(new PaymentAttemptResponse(attempt, ORDER,
                "COD", 125000, "SMTEST", "PENDING", null, null, null));
        mvc.perform(post(PATH).header("X-StyleMind-Service-Token", "test-only-handoff")
                .contentType(MediaType.APPLICATION_JSON).content(body("COD", "PLACED")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(attempt.toString()))
                .andExpect(jsonPath("$.orderId").value(ORDER.toString()))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Location", "/api/v1/payments/attempts/" + attempt));
    }

    @Test
    void rejectsUnknownNullDecimalOverflowInvalidUuidKeyAndMethodState() throws Exception {
        String valid = body("COD", "PLACED");
        for (String invalid : java.util.List.of(
                valid.replace("125000", "0"), valid.replace("125000", "125000.1"),
                valid.replace("125000", "9223372036854775808"), valid.replace("125000", "\"125000\""),
                valid.replace("\"COD\"", "null"), valid.replace(CUSTOMER.toString(), "1-1-1-1-1"),
                valid.replace("initial-", "other-"), valid.replace("PLACED", "PAID"),
                body("SEPAY_VIETQR", "PLACED"), valid.replace("\"customer_id\"", "\"unexpected\""),
                valid.replace("125000", "125000,\"unknown\":true"))) {
            mvc.perform(post(PATH).header("X-StyleMind-Service-Token", "test-only-handoff")
                    .contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        verifyNoInteractions(payments);
    }
}
