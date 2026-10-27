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
        "stylemind.payment.sepay.qr-bank-name=TESTBANK",
        "stylemind.payment.sepay.qr-account-number=000-test-only"})
@Testcontainers
@Import(PaymentCreationHttpPostgresTests.JwtConfig.class)
class PaymentCreationHttpPostgresTests {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:18.6")
            .withDatabaseName("payment_runtime_test").withUsername("payment_test").withPassword("test-only");
    static final RSAKey SIGNING_KEY = signingKey();
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.stylemind.payment.client.CommerceOrderClient commerce;
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void resetIsolatedDatabase() {
        jdbc.update("DELETE FROM public.payment_outbox");
        jdbc.update("DELETE FROM public.payment_exception");
        jdbc.update("DELETE FROM public.financial_transaction");
        jdbc.update("DELETE FROM public.payment_attempt");
    }

    @Test
    void qrCreationAndReplayUseOrderAmountWithoutRecordingMoney() {
        UUID order = UUID.randomUUID(), owner = UUID.randomUUID();
        var first = push(order, owner, "SEPAY_VIETQR");
        var replay = push(order, owner, "SEPAY_VIETQR");
        assertThat(replay.path("id").asText()).isEqualTo(first.path("id").asText());
        assertThat(first.path("qrPayload").asText()).contains("amount=125000");
        assertThat(first.path("status").asText()).isEqualTo("PENDING");
        assertThat(first.path("transferContent").asText()).isEqualTo(first.path("reference").asText());
        assertThat(jdbc.queryForObject("SELECT extract(epoch from expires_at-generated_at)::bigint FROM public.payment_attempt", Long.class))
                .isEqualTo(900L);
        assertThat(count("payment_attempt")).isEqualTo(1);
        assertThat(count("financial_transaction")).isZero();
        assertThat(count("payment_exception")).isZero();
        assertThat(count("payment_outbox")).isZero();
        org.mockito.Mockito.verifyNoInteractions(commerce);
    }

    @Test
    void publicCreationRejectsForeignOwnerAndAcceptsAuthoritativeOrderAmount() {
        UUID order = UUID.randomUUID(), owner = UUID.randomUUID();
        org.mockito.Mockito.when(commerce.getPaymentSnapshot(order)).thenReturn(
                new com.stylemind.payment.client.CommerceOrderPaymentSnapshot(order, owner,
                        com.stylemind.payment.model.PaymentMethod.SEPAY_VIETQR, 237000, "PENDING_PAYMENT"));
        var request = Map.of("orderId", order.toString(), "method", "SEPAY_VIETQR", "requestKey", "public-test");
        assertThat(post("/api/v1/payments/attempts", request, "Authorization", "Bearer " + token(UUID.randomUUID(), "CUSTOMER"))
                .getStatusCode().value()).isEqualTo(403);
        assertThat(count("payment_attempt")).isZero();
        var created = post("/api/v1/payments/attempts", request, "Authorization", "Bearer " + token(owner, "CUSTOMER"));
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(created.getBody().path("amountVnd").asLong()).isEqualTo(237000);
        assertThat(created.getBody().path("qrPayload").asText()).contains("amount=237000");
        assertThat(post("/api/v1/payments/attempts", request, "Authorization", "Bearer " + token(owner, "CUSTOMER"))
                .getBody().path("id").asText()).isEqualTo(created.getBody().path("id").asText());
        assertThat(count("payment_attempt")).isEqualTo(1);
    }

    @Test
    void publicLookupFailureOrMissingJwtDoesNotCreateAttempt() {
        UUID order = UUID.randomUUID(), owner = UUID.randomUUID();
        var request = Map.of("orderId", order.toString(), "method", "SEPAY_VIETQR", "requestKey", "lookup-test");
        assertThat(post("/api/v1/payments/attempts", request, null, null).getStatusCode().value()).isEqualTo(401);
        org.mockito.Mockito.when(commerce.getPaymentSnapshot(order)).thenThrow(
                new com.stylemind.payment.errors.PaymentApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                        "COMMERCE_ORDER_LOOKUP_FAILED", "Commerce order payment snapshot lookup failed"));
        var response = post("/api/v1/payments/attempts", request, "Authorization", "Bearer " + token(owner, "CUSTOMER"));
        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(response.getBody().path("code").asText()).isEqualTo("COMMERCE_ORDER_LOOKUP_FAILED");
        assertThat(count("payment_attempt")).isZero();
    }

    @Test
    void incorrectHandoffCredentialAndInvalidAmountsCannotWriteAttempt() {
        var body = Map.of("order_id", UUID.randomUUID(), "customer_id", UUID.randomUUID(), "accepted_total_vnd", 125000,
                "payment_method", "SEPAY_VIETQR", "order_status", "PENDING_PAYMENT", "request_key", "invalid-key");
        assertThat(post("/internal/v1/payments/order-attempts", body, null, null).getStatusCode().value()).isEqualTo(401);
        assertThat(post("/internal/v1/payments/order-attempts", body, "X-StyleMind-Service-Token", "incorrect-test-only")
                .getStatusCode().value()).isEqualTo(401);
        assertThat(post("/internal/v1/payments/order-attempts", body, "X-StyleMind-Service-Token", "runtime-test-only-handoff")
                .getStatusCode().value()).isEqualTo(400);
        assertThat(count("payment_attempt")).isZero();
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
