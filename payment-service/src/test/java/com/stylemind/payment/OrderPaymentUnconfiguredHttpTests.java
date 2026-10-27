package com.stylemind.payment;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stylemind.payment.service.PaymentAttemptService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"management.health.db.enabled=false", "spring.flyway.enabled=false",
        "spring.datasource.hikari.initialization-fail-timeout=-1", "stylemind.payment.handoff-token="})
@AutoConfigureMockMvc
class OrderPaymentUnconfiguredHttpTests {
    @Autowired MockMvc mvc;
    @MockitoBean PaymentAttemptService payments;

    @Test
    void blankConfiguredSecretFailsClosedEvenWithSuppliedHeader() throws Exception {
        mvc.perform(post(OrderPaymentHttpTests.PATH).header("X-StyleMind-Service-Token", "anything"))
                .andExpect(status().isServiceUnavailable());
        verifyNoInteractions(payments);
    }
}
