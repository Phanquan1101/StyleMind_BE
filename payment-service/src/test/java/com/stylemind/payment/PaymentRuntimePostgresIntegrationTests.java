package com.stylemind.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// Real HTTP, security, services and PostgreSQL. Only synthetic fixtures and disposable resources.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "stylemind.payment.handoff-token=runtime-test-only-handoff",
        "stylemind.payment.sepay.webhook-auth-mode=api-key",
        "stylemind.payment.sepay.webhook-api-key=runtime-test-only-sepay",
        "stylemind.payment.sepay.qr-bank-name=TESTBANK",
        "stylemind.payment.sepay.qr-account-number=000-test-only"})
@Testcontainers
@Import(PaymentRuntimePostgresIntegrationTests.JwtConfig.class)
class PaymentRuntimePostgresIntegrationTests {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:18.6")
            .withDatabaseName("payment_runtime_test").withUsername("payment_test").withPassword("test-only");
    static final AtomicLong EVENTS = new AtomicLong(100000);
    static final RSAKey SIGNING_KEY = signingKey();
    static final String WEBHOOK = "/internal/v1/sepay/webhooks";
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void resetIsolatedDatabase() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_outbox_test ON public.payment_outbox");
        jdbc.update("DELETE FROM public.payment_outbox");
        jdbc.update("DELETE FROM public.payment_exception");
        jdbc.update("DELETE FROM public.financial_transaction");
        jdbc.update("DELETE FROM public.payment_attempt");
    }

    @Test
    void qrCreationReplayWebhookSettlementAndDuplicateDoNotDoubleCredit() {
        UUID order = UUID.randomUUID();
        UUID customer = UUID.randomUUID();
        var first = push(order, customer, "SEPAY_VIETQR");
        var replay = push(order, customer, "SEPAY_VIETQR");
        assertThat(replay.path("id").asText()).isEqualTo(first.path("id").asText());
        assertThat(first.path("qrPayload").asText()).contains("amount=125000");
        assertThat(count("payment_attempt")).isEqualTo(1);
        var webhook = webhook(first.path("reference").asText(), 125000);
        var accepted = post(WEBHOOK, webhook, "Authorization", "Apikey runtime-test-only-sepay");
        assertThat(accepted.getStatusCode().value()).isEqualTo(200);
        assertThat(accepted.getBody().path("success").asBoolean()).isTrue();
        assertThat(attemptStatus()).isEqualTo("SETTLED");
        assertThat(count("financial_transaction")).isEqualTo(1);
        assertThat(count("payment_outbox")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT actual_amount_vnd FROM public.financial_transaction", Long.class))
                .isEqualTo(125000L);
        var duplicate = post(WEBHOOK, webhook, "Authorization", "Apikey runtime-test-only-sepay");
        assertThat(duplicate.getStatusCode().value()).isEqualTo(200);
        assertThat(count("financial_transaction")).isEqualTo(1);
        assertThat(count("payment_outbox")).isEqualTo(1);
        assertThat(attemptStatus()).isEqualTo("SETTLED");
    }

    @Test
    void invalidWebhookCredentialsAndMalformedJsonCannotChangePayment() {
        var attempt = push(UUID.randomUUID(), UUID.randomUUID(), "SEPAY_VIETQR");
        var payload = webhook(attempt.path("reference").asText(), 125000);
        assertThat(post(WEBHOOK, payload, "Authorization", "Apikey incorrect-test-value").getStatusCode().value())
                .isEqualTo(401);
        assertThat(post(WEBHOOK, payload, null, null).getStatusCode().value()).isEqualTo(401);
        assertThat(post(WEBHOOK, "not-json", "Authorization", "Apikey runtime-test-only-sepay")
                .getStatusCode().value()).isEqualTo(400);
        assertThat(attemptStatus()).isEqualTo("PENDING");
        assertThat(count("financial_transaction")).isZero();
        assertThat(count("payment_outbox")).isZero();
        assertThat(count("payment_exception")).isZero();
    }

    @Test
    void mismatchedAmountIsNotCreditedAndObservedExceptionIsRecorded() {
        var attempt = push(UUID.randomUUID(), UUID.randomUUID(), "SEPAY_VIETQR");
        var response = post(WEBHOOK, webhook(attempt.path("reference").asText(), 1),
                "Authorization", "Apikey runtime-test-only-sepay");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(count("financial_transaction")).isZero();
        assertThat(count("payment_outbox")).isZero();
        assertThat(jdbc.queryForObject("SELECT exception_reason FROM public.payment_exception", String.class))
                .isEqualTo("AMOUNT_MISMATCH");
        // Characterizes current implementation, not approval of a new exception-resolution policy.
        assertThat(attemptStatus()).isEqualTo("REQUIRES_RECONCILIATION");
    }

    @Test
    void fractionalStringOverflowMissingAndDuplicateAmountFieldsCannotSettle() {
        var attempt = push(UUID.randomUUID(), UUID.randomUUID(), "SEPAY_VIETQR");
        String valid = "{\"id\":200001,\"code\":\"" + attempt.path("reference").asText()
                + "\",\"transferType\":\"in\",\"transferAmount\":125000}";
        for (String invalid : List.of(valid.replace("125000", "125000.9"), valid.replace("125000", "\"125000\""),
                valid.replace("125000", "9223372036854775808"), valid.replace("125000", "null"),
                valid.replace("125000", "0"), valid.replace("125000", "-1"),
                valid.replace("125000", "125000,\"transferAmount\":1"), "null", valid + " {}")) {
            assertThat(post(WEBHOOK, invalid, "Authorization", "Apikey runtime-test-only-sepay").getStatusCode().value())
                    .isEqualTo(400);
        }
        assertThat(count("financial_transaction")).isZero(); assertThat(count("payment_outbox")).isZero();
        assertThat(count("payment_exception")).isZero(); assertThat(attemptStatus()).isEqualTo("PENDING");
    }

    @Test
    void sequentialAndConcurrentWebhookReplaysHaveOneLedgerAndOutboxWithoutFalseException() throws Exception {
        var attempt = push(UUID.randomUUID(), UUID.randomUUID(), "SEPAY_VIETQR");
        var payload = webhook(attempt.path("reference").asText(), 125000);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var jobs = new java.util.ArrayList<java.util.concurrent.Future<ResponseEntity<JsonNode>>>();
            for (int i = 0; i < 4; i++) jobs.add(pool.submit(() -> post(WEBHOOK, payload, "Authorization", "Apikey runtime-test-only-sepay")));
            for (var job : jobs) assertThat(job.get(30, java.util.concurrent.TimeUnit.SECONDS).getStatusCode().value()).isEqualTo(200);
        }
        for (int i = 0; i < 3; i++) assertThat(post(WEBHOOK, payload, "Authorization", "Apikey runtime-test-only-sepay")
                .getStatusCode().value()).isEqualTo(200);
        assertThat(count("financial_transaction")).isEqualTo(1); assertThat(count("payment_outbox")).isEqualTo(1);
        assertThat(count("payment_exception")).isZero(); assertThat(attemptStatus()).isEqualTo("SETTLED");
    }

    @Test
    void distinctProviderEventsCannotCreditOneAttemptTwice() throws Exception {
        var attempt = push(UUID.randomUUID(), UUID.randomUUID(), "SEPAY_VIETQR");
        String reference = attempt.path("reference").asText();
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> post(WEBHOOK, webhook(reference, 125000), "Authorization", "Apikey runtime-test-only-sepay"));
            var b = pool.submit(() -> post(WEBHOOK, webhook(reference, 125000), "Authorization", "Apikey runtime-test-only-sepay"));
            assertThat(a.get(30, java.util.concurrent.TimeUnit.SECONDS).getStatusCode().value()).isEqualTo(200);
            assertThat(b.get(30, java.util.concurrent.TimeUnit.SECONDS).getStatusCode().value()).isEqualTo(200);
        }
        assertThat(count("financial_transaction")).isEqualTo(1); assertThat(count("payment_outbox")).isEqualTo(1);
        assertThat(count("payment_exception")).isEqualTo(1); assertThat(attemptStatus()).isEqualTo("SETTLED");
    }

    @Test
    void providerEventReuseOnAnotherAttemptDoesNotAbortExceptionTransaction() {
        var a = push(UUID.randomUUID(), UUID.randomUUID(), "SEPAY_VIETQR");
        var b = push(UUID.randomUUID(), UUID.randomUUID(), "SEPAY_VIETQR");
        var payload = webhook(a.path("reference").asText(), 125000);
        assertThat(post(WEBHOOK, payload, "Authorization", "Apikey runtime-test-only-sepay").getStatusCode().value()).isEqualTo(200);
        var reused = new java.util.HashMap<>(payload); reused.put("code", b.path("reference").asText());
        assertThat(post(WEBHOOK, reused, "Authorization", "Apikey runtime-test-only-sepay").getStatusCode().value()).isEqualTo(200);
        assertThat(count("financial_transaction")).isEqualTo(1); assertThat(count("payment_outbox")).isEqualTo(1);
        assertThat(count("payment_exception")).isEqualTo(1);
    }

    @Test
    void lateUnmatchedAndCodReferenceTransfersAreRecordedWithoutCrediting() {
        var attempt = push(UUID.randomUUID(), UUID.randomUUID(), "SEPAY_VIETQR");
        Instant generated = Instant.now().minusSeconds(3600);
        jdbc.update("UPDATE public.payment_attempt SET generated_at=?,expires_at=?", java.sql.Timestamp.from(generated),
                java.sql.Timestamp.from(generated.plusSeconds(900)));
        assertThat(post(WEBHOOK, webhook(attempt.path("reference").asText(), 125000), "Authorization", "Apikey runtime-test-only-sepay")
                .getStatusCode().value()).isEqualTo(200);
        post(WEBHOOK, webhook("SMUNMATCHEDTEST", 125000), "Authorization", "Apikey runtime-test-only-sepay");
        var cod = push(UUID.randomUUID(), UUID.randomUUID(), "COD");
        post(WEBHOOK, webhook(cod.path("reference").asText(), 125000), "Authorization", "Apikey runtime-test-only-sepay");
        assertThat(jdbc.queryForList("SELECT exception_reason FROM public.payment_exception", String.class))
                .containsExactlyInAnyOrder("LATE_PAYMENT", "UNMATCHED_REFERENCE", "PAYMENT_METHOD_MISMATCH");
        assertThat(count("financial_transaction")).isZero(); assertThat(count("payment_outbox")).isZero();
    }

    @Test
    void invalidTimestampIsRejectedAndExpiryBoundaryIsLate() {
        var attempt = push(UUID.randomUUID(), UUID.randomUUID(), "SEPAY_VIETQR");
        var payload = new java.util.HashMap<>(webhook(attempt.path("reference").asText(), 125000));
        payload.put("transactionDate", "2026-02-30 00:00:00");
        assertThat(post(WEBHOOK, payload, "Authorization", "Apikey runtime-test-only-sepay").getStatusCode().value()).isEqualTo(400);
        Instant generated = Instant.parse("2026-10-26T00:00:00Z");
        jdbc.update("UPDATE public.payment_attempt SET generated_at=?,expires_at=?", java.sql.Timestamp.from(generated),
                java.sql.Timestamp.from(generated.plusSeconds(900)));
        payload.put("transactionDate", "2026-10-26 07:15:00");
        assertThat(post(WEBHOOK, payload, "Authorization", "Apikey runtime-test-only-sepay").getStatusCode().value()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT exception_reason FROM public.payment_exception", String.class)).isEqualTo("LATE_PAYMENT");
        assertThat(count("financial_transaction")).isZero();
    }

    @Test
    void failedOutboxWriteRollsBackSettlementAndCanRetrySameWebhook() {
        var attempt = push(UUID.randomUUID(), UUID.randomUUID(), "SEPAY_VIETQR");
        var payload = webhook(attempt.path("reference").asText(), 125000);
        jdbc.execute("CREATE OR REPLACE FUNCTION public.fail_outbox_test_fn() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test-only failure'; END $$");
        jdbc.execute("CREATE TRIGGER fail_outbox_test BEFORE INSERT ON public.payment_outbox FOR EACH ROW EXECUTE FUNCTION public.fail_outbox_test_fn()");
        try {
            assertThat(post(WEBHOOK, payload, "Authorization", "Apikey runtime-test-only-sepay").getStatusCode().value()).isEqualTo(500);
            assertThat(attemptStatus()).isEqualTo("PENDING"); assertThat(count("financial_transaction")).isZero();
        } finally { jdbc.execute("DROP TRIGGER fail_outbox_test ON public.payment_outbox"); }
        assertThat(post(WEBHOOK, payload, "Authorization", "Apikey runtime-test-only-sepay").getStatusCode().value()).isEqualTo(200);
        assertThat(count("financial_transaction")).isEqualTo(1); assertThat(count("payment_outbox")).isEqualTo(1);
    }

    @Test
    void exceptionListSupportsDefaultAndFilteredQueriesAndDeniesNonAdmins() {
        var attempt = push(UUID.randomUUID(), UUID.randomUUID(), "SEPAY_VIETQR");
        post(WEBHOOK, webhook(attempt.path("reference").asText(), 1), "Authorization", "Apikey runtime-test-only-sepay");
        String path = "/api/v1/admin/payment-exceptions";
        assertThat(http.getForEntity(path, JsonNode.class).getStatusCode().value()).isEqualTo(401);
        var headers = new HttpHeaders(); headers.setBearerAuth(token(UUID.randomUUID(), "CUSTOMER"));
        assertThat(http.exchange(path, org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class)
                .getStatusCode().value()).isEqualTo(403);
        headers.setBearerAuth(token(UUID.randomUUID(), "ADMIN"));
        for (String query : List.of("", "?status=OPEN", "?limit=1&offset=0")) {
            var listed = http.exchange(path + query, org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
            assertThat(listed.getStatusCode().value()).isEqualTo(200);
            assertThat(listed.getBody().path("items").size()).isEqualTo(1);
            assertThat(listed.getHeaders().getCacheControl()).isEqualTo("no-store");
        }
        for (String query : List.of("?limit=0", "?offset=-1", "?limit=101", "?limit=not-an-integer"))
            assertThat(http.exchange(path + query, org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class)
                    .getStatusCode().value()).isEqualTo(400);
    }

    private JsonNode push(UUID order, UUID customer, String method) {
        var body = Map.of("order_id", order, "customer_id", customer, "accepted_total_vnd", 125000,
                "payment_method", method, "order_status", method.equals("COD") ? "PLACED" : "PENDING_PAYMENT",
                "request_key", "initial-" + order);
        var response = post("/internal/v1/payments/order-attempts", body,
                "X-StyleMind-Service-Token", "runtime-test-only-handoff");
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return response.getBody();
    }
    private Map<String, Object> webhook(String reference, long amount) {
        return Map.of("id", EVENTS.incrementAndGet(), "gateway", "TESTBANK", "accountNumber", "000-test-only",
                "code", reference, "transferType", "in", "transferAmount", amount,
                "referenceCode", UUID.randomUUID().toString());
    }
    private ResponseEntity<JsonNode> post(String path, Object body, String header, String value) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (header != null) headers.set(header, value);
        return http.postForEntity(path, new HttpEntity<>(body, headers), JsonNode.class);
    }
    private int count(String table) {
        // Only fixed, test-owned table names from calls in this class, never request input.
        return jdbc.queryForObject("SELECT count(*) FROM public." + table, Integer.class);
    }
    private String attemptStatus() {
        return jdbc.queryForObject("SELECT attempt_status FROM public.payment_attempt", String.class);
    }
    private String token(UUID subject, String role) {
        var claims = JwtClaimsSet.builder().subject(subject.toString()).issuedAt(Instant.now().minusSeconds(1))
                .expiresAt(Instant.now().plusSeconds(300)).claim("roles", List.of(role)).build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(SIGNING_KEY.getKeyID()).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(SIGNING_KEY)))
                .encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
    private static RSAKey signingKey() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var pair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID("runtime-test-only").build();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class JwtConfig {
        @Bean @Primary JwtDecoder testDecoder() throws Exception {
            return NimbusJwtDecoder.withPublicKey(SIGNING_KEY.toRSAPublicKey()).build();
        }
    }
}
