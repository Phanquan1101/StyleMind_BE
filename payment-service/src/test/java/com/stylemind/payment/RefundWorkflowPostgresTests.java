package com.stylemind.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import com.stylemind.payment.service.RefundResultDispatcher;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "stylemind.payment.commerce.service-token=refund-test-only",
        "stylemind.payment.refund-encryption-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="})
@AutoConfigureMockMvc
@Testcontainers
class RefundWorkflowPostgresTests {

    @Container
    static final PostgreSQLContainer<?> DB = IsolatedPostgres.create("postgres:18.6")
            .withDatabaseName("payment_refund_test").withUsername("payment_test").withPassword("test-only");
    static final HttpServer COMMERCE = commerceStub();
    static final Map<String, String> SOURCES = new ConcurrentHashMap<>();
    static final AtomicInteger RESULT_STATUS = new AtomicInteger();
    static final AtomicInteger RESULT_REQUESTS = new AtomicInteger();
    static final AtomicReference<String> RESULT_BODY = new AtomicReference<>();

    static final String DESTINATION = """
            {"bank_name":"Synthetic Bank","account_number":"000112233","account_holder":"Test Owner"}
            """;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
        registry.add("stylemind.payment.commerce.base-url", () -> "http://127.0.0.1:" + COMMERCE.getAddress().getPort());
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired RefundResultDispatcher dispatcher;
    @MockitoBean JwtDecoder jwtDecoder;

    @BeforeEach
    void credentialsAndStub() {
        SOURCES.clear();
        jdbc.execute("TRUNCATE TABLE public.refund_result_outbox, public.refund_audit, public.refund_case");
        RESULT_REQUESTS.set(0);
        RESULT_STATUS.set(503);
        RESULT_BODY.set(null);
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            var identity = token.split("\\.", 2);
            return Jwt.withTokenValue(token).header("alg", "test")
                    .subject(identity[0]).claim("roles", java.util.List.of(identity[1])).build();
        });
    }

    @AfterAll
    static void stopCommerce() {
        COMMERCE.stop(0);
    }

    @Test
    void onlyOwnerCanSupplyDetailsAndOnlyAssignedProcessingAdminCanReadThem() throws Exception {
        var ledger = settled(100_000);
        UUID source = source("RETURN", ledger, 50_000);
        String customer = user(ledger.customer, "ROLE_CUSTOMER");
        String otherCustomer = user(UUID.randomUUID(), "ROLE_CUSTOMER");
        String admin = user(UUID.randomUUID(), "ROLE_ADMIN");
        String otherAdmin = user(UUID.randomUUID(), "ROLE_ADMIN");
        String refund = path("RETURN", source);
        String processing = adminPath("RETURN", source);
        mvc.perform(post(refund + "/prepare")).andExpect(status().isUnauthorized());
        mvc.perform(auth(get(refund), customer)).andExpect(status().isNotFound());
        mvc.perform(auth(post(refund + "/prepare"), otherCustomer)).andExpect(status().isNotFound());
        String id = prepare(refund, customer);
        SOURCES.clear();
        mvc.perform(auth(post(refund + "/prepare"), customer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.refund_id").value(id));
        mvc.perform(auth(get(refund), otherCustomer)).andExpect(status().isNotFound());
        mvc.perform(auth(put(refund + "/destination"), admin, DESTINATION)).andExpect(status().isNotFound());
        mvc.perform(auth(put(refund + "/destination"), customer, DESTINATION.replace("\"000112233\"", "112233")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(auth(put(refund + "/destination"), customer, DESTINATION)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.bank_name").doesNotExist());
        String ciphertext = jdbc.queryForObject("SELECT encode(destination_ciphertext, 'base64') FROM public.refund_case WHERE refund_id = ?",
                String.class, UUID.fromString(id));
        assertThat(ciphertext).doesNotContain("000112233", "Test Owner", "Synthetic Bank");

        mvc.perform(auth(put(refund + "/destination"), customer, DESTINATION)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT encode(destination_ciphertext, 'base64') FROM public.refund_case WHERE refund_id = ?",
                String.class, UUID.fromString(id))).isNotEqualTo(ciphertext);
        mvc.perform(auth(get(processing + "/destination"), admin)).andExpect(status().isConflict());
        mvc.perform(auth(post(processing + "/start"), customer)).andExpect(status().isForbidden());
        mvc.perform(auth(post(processing + "/start"), admin)).andExpect(status().isOk());
        mvc.perform(auth(post(processing + "/start"), admin)).andExpect(status().isOk());
        mvc.perform(auth(post(processing + "/start"), otherAdmin)).andExpect(status().isForbidden());
        mvc.perform(auth(put(refund + "/destination"), customer, DESTINATION)).andExpect(status().isConflict());
        mvc.perform(auth(get(processing + "/destination"), otherAdmin)).andExpect(status().isForbidden());
        mvc.perform(auth(get(processing + "/destination"), admin)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.bank_name").value("Synthetic Bank"))
                .andExpect(jsonPath("$.account_number").value("000112233"))
                .andExpect(jsonPath("$.account_holder").value("Test Owner"));
        mvc.perform(auth(get(refund), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.account_holder").doesNotExist());
    }

    @Test
    void concurrentSourcesCannotAllocateMoreThanTheSameSettledTransaction() throws Exception {
        var ledger = settled(100_000);
        String refund1 = path("RETURN", source("RETURN", ledger, 60_000));
        String refund2 = path("CANCELLATION", source("CANCELLATION", ledger, 60_000));
        String customer = user(ledger.customer, "ROLE_CUSTOMER");
        var together = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> prepareConcurrently(refund1, customer, together));
            var second = executor.submit(() -> prepareConcurrently(refund2, customer, together));
            together.countDown();
            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject("""
                SELECT SUM(amount_vnd)::bigint FROM public.refund_case c
                JOIN public.financial_transaction f ON f.transaction_id = c.transaction_id
                WHERE f.payment_attempt_id = ?
                """, Long.class, ledger.attempt)).isEqualTo(60_000L);
    }

    @Test
    void mismatchedOrderOrUnsettledAttemptFailsClosed() throws Exception {
        var ledger = settled(100_000);
        var mismatched = new Ledger(UUID.randomUUID(), ledger.customer, ledger.attempt);
        var differentOwner = new Ledger(ledger.order, UUID.randomUUID(), ledger.attempt);
        mvc.perform(auth(post(path("RETURN", source("RETURN", mismatched, 50_000)) + "/prepare"),
                user(ledger.customer, "ROLE_CUSTOMER"))).andExpect(status().isConflict());
        mvc.perform(auth(post(path("RETURN", source("RETURN", differentOwner, 50_000)) + "/prepare"),
                user(differentOwner.customer, "ROLE_CUSTOMER"))).andExpect(status().isConflict());
        jdbc.update("UPDATE public.payment_attempt SET attempt_status = 'REQUIRES_RECONCILIATION' WHERE payment_attempt_id = ?", ledger.attempt);
        mvc.perform(auth(post(path("RETURN", source("RETURN", ledger, 50_000)) + "/prepare"),
                user(ledger.customer, "ROLE_CUSTOMER"))).andExpect(status().isConflict());
    }

    @Test
    void confirmsOnceAndRetriesTheSameSanitizedResultAfterCommerceFailure() throws Exception {
        var ledger = settled(100_000);
        UUID source = source("CANCELLATION", ledger, 75_000);
        String refund = path("CANCELLATION", source);
        String processing = adminPath("CANCELLATION", source);
        String customer = user(ledger.customer, "ROLE_CUSTOMER");
        String admin = user(UUID.randomUUID(), "ROLE_ADMIN");
        String otherAdmin = user(UUID.randomUUID(), "ROLE_ADMIN");
        String id = prepare(refund, admin);

        mvc.perform(auth(put(refund + "/destination"), customer, DESTINATION)).andExpect(status().isOk());
        mvc.perform(auth(post(processing + "/start"), admin)).andExpect(status().isOk());
        mvc.perform(auth(post(processing + "/fail"), otherAdmin, "{\"reason\":\"network issue\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(auth(post(processing + "/fail"), admin, "{\"reason\":\"network issue\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));
        mvc.perform(auth(post(processing + "/start"), otherAdmin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
        String evidence = "{\"transfer_reference\":\"TRANSFER-001\",\"proof_reference\":\"proof-001\"}";
        mvc.perform(auth(post(processing + "/complete"), admin, evidence)).andExpect(status().isForbidden());
        mvc.perform(auth(post(processing + "/complete"), otherAdmin, evidence)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.proof_reference").doesNotExist());
        mvc.perform(auth(post(processing + "/complete"), otherAdmin, evidence)).andExpect(status().isOk());
        mvc.perform(auth(post(processing + "/complete"), otherAdmin, evidence.replace("001", "002")))
                .andExpect(status().isConflict());
        mvc.perform(auth(get(processing + "/destination"), otherAdmin)).andExpect(status().isConflict());
        assertThat(jdbc.queryForList("SELECT action FROM public.refund_audit WHERE refund_id = ? ORDER BY audit_id",
                String.class, UUID.fromString(id))).containsExactly("PREPARED", "DESTINATION_SAVED", "STARTED", "FAILED", "RETRIED", "REFUNDED");

        assertThat(dispatcher.dispatchPending()).isZero();
        assertThat(RESULT_REQUESTS.get()).isEqualTo(1);
        String payload = RESULT_BODY.get();
        JsonNode result = mapper.readTree(payload);
        assertThat(result.size()).isEqualTo(6);
        assertThat(result.path("refund_id").asText()).isEqualTo(id);
        assertThat(result.path("source_type").asText()).isEqualTo("CANCELLATION");
        assertThat(result.path("source_id").asText()).isEqualTo(source.toString());
        assertThat(result.path("order_id").asText()).isEqualTo(ledger.order.toString());
        assertThat(result.path("amount_vnd").asLong()).isEqualTo(75_000);
        assertThat(result.path("refunded_at").asText()).isNotBlank();
        assertThat(payload).doesNotContain("000112233", "Synthetic Bank", "Test Owner", "proof-001");
        RESULT_STATUS.set(204);
        jdbc.update("UPDATE public.refund_result_outbox SET next_attempt_at = now() - interval '1 second' WHERE refund_id = ?",
                UUID.fromString(id));
        assertThat(dispatcher.dispatchPending()).isEqualTo(1);
        assertThat(RESULT_REQUESTS.get()).isEqualTo(2);
        assertThat(RESULT_BODY.get()).isEqualTo(payload);
        assertThat(dispatcher.dispatchPending()).isZero();
    }

    @Test
    void transferReferenceCanOnlyConfirmOneCaseAndProofIsRequired() throws Exception {
        var ledger = settled(100_000);
        UUID first = source("RETURN", ledger, 40_000);
        UUID second = source("RETURN", ledger, 60_000);
        String customer = user(ledger.customer, "ROLE_CUSTOMER");
        String admin = user(UUID.randomUUID(), "ROLE_ADMIN");
        for (UUID source : java.util.List.of(first, second)) {
            String refund = path("RETURN", source);
            prepare(refund, customer);
            mvc.perform(auth(put(refund + "/destination"), customer, DESTINATION)).andExpect(status().isOk());
            mvc.perform(auth(post(adminPath("RETURN", source) + "/start"), admin)).andExpect(status().isOk());
        }
        String firstComplete = adminPath("RETURN", first) + "/complete";
        String secondComplete = adminPath("RETURN", second) + "/complete";
        mvc.perform(auth(post(firstComplete), admin, "{\"transfer_reference\":\"unique-test\"}"))
                .andExpect(status().isBadRequest());
        String evidence = "{\"transfer_reference\":\"unique-test\",\"proof_reference\":\"proof-test\"}";
        mvc.perform(auth(post(firstComplete), admin, evidence)).andExpect(status().isOk());
        mvc.perform(auth(post(secondComplete), admin, evidence)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_TRANSFER_REFERENCE_IN_USE"));
        mvc.perform(auth(get(path("RETURN", second)), customer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM public.refund_result_outbox", Long.class)).isEqualTo(1L);
    }

    @Test
    void failedCaseKeepsItsAllocationForAnExplicitRetry() throws Exception {
        var ledger = settled(100_000);
        UUID source = source("RETURN", ledger, 75_000);
        String refund = path("RETURN", source);
        String adminPath = adminPath("RETURN", source);
        String customer = user(ledger.customer, "ROLE_CUSTOMER");
        String admin = user(UUID.randomUUID(), "ROLE_ADMIN");
        prepare(refund, customer);
        mvc.perform(auth(put(refund + "/destination"), customer, DESTINATION)).andExpect(status().isOk());
        mvc.perform(auth(post(adminPath + "/start"), admin)).andExpect(status().isOk());
        mvc.perform(auth(post(adminPath + "/fail"), admin, "{\"reason\":\"Bank declined the transfer\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));
        mvc.perform(auth(post(path("RETURN", source("RETURN", ledger, 25_001)) + "/prepare"), customer))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REFUND_AMOUNT_EXCEEDED"));
        prepare(path("RETURN", source("RETURN", ledger, 25_000)), customer);
    }

    private int prepareConcurrently(String path, String customer, CountDownLatch together) throws Exception {
        together.await();
        return mvc.perform(auth(post(path + "/prepare"), customer)).andReturn().getResponse().getStatus();
    }

    private String prepare(String path, String token) throws Exception {
        var response = mvc.perform(auth(post(path + "/prepare"), token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_DETAILS")).andReturn().getResponse();
        return mapper.readTree(response.getContentAsByteArray()).path("refund_id").asText();
    }

    private Ledger settled(long amount) {
        var ledger = new Ledger(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        jdbc.update("""
                INSERT INTO public.payment_attempt (payment_attempt_id, order_id, customer_id, payment_reference,
                  request_key, payment_method, expected_amount_vnd, attempt_status, generated_at, confirmed_at)
                VALUES (?, ?, ?, ?, ?, 'COD', ?, 'SETTLED', now(), now())
                """, ledger.attempt, ledger.order, ledger.customer, ledger.attempt.toString(), "test", amount);
        jdbc.update("""
                INSERT INTO public.financial_transaction (transaction_id, payment_attempt_id, actual_amount_vnd, settled_at)
                VALUES (?, ?, ?, now())
                """, UUID.randomUUID(), ledger.attempt, amount);
        return ledger;
    }

    private UUID source(String type, Ledger ledger, long amount) {
        UUID source = UUID.randomUUID();
        SOURCES.put(type + "/" + source, """
                {"source_type":"%s","source_id":"%s","order_id":"%s","customer_id":"%s",
                 "payment_attempt_id":"%s","amount_vnd":%d}
                """.formatted(type, source, ledger.order, ledger.customer, ledger.attempt, amount));
        return source;
    }

    private static String user(UUID id, String role) {
        return id + ":" + role;
    }

    private static String path(String type, UUID id) {
        return "/api/v1/refunds/" + type + "/" + id;
    }

    private static String adminPath(String type, UUID id) {
        return "/api/v1/admin/refunds/" + type + "/" + id;
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token.replace(":", "."));
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token, String body) {
        return auth(request, token).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static HttpServer commerceStub() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/internal/v1/refund-sources/", exchange -> {
                try (exchange) {
                    String key = exchange.getRequestURI().getPath().substring("/internal/v1/refund-sources/".length());
                    String body = SOURCES.get(key);
                    if (!"GET".equals(exchange.getRequestMethod())
                            || !"refund-test-only".equals(exchange.getRequestHeaders().getFirst("X-StyleMind-Service-Token"))) {
                        exchange.sendResponseHeaders(403, -1);
                    } else if (body == null) {
                        exchange.sendResponseHeaders(404, -1);
                    } else {
                        byte[] json = body.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, json.length);
                        exchange.getResponseBody().write(json);
                    }
                }
            });
            server.createContext("/internal/v1/refund-results", exchange -> {
                try (exchange) {
                    if (!"POST".equals(exchange.getRequestMethod())
                            || !"refund-test-only".equals(exchange.getRequestHeaders().getFirst("X-StyleMind-Service-Token"))) {
                        exchange.sendResponseHeaders(403, -1);
                        return;
                    }
                    RESULT_REQUESTS.incrementAndGet();
                    RESULT_BODY.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    exchange.sendResponseHeaders(RESULT_STATUS.get(), -1);
                }
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Ledger(UUID order, UUID customer, UUID attempt) { }
}




