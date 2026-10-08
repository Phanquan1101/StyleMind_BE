package com.stylemind.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.stylemind.commerce.logging.CorrelationIdFilter;
import io.swagger.v3.oas.annotations.Hidden;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(properties = {
        "management.health.db.enabled=false",
        "spring.datasource.hikari.initialization-fail-timeout=-1"
})
@AutoConfigureMockMvc
@Import(CommerceServiceApplicationTests.JwtTestConfiguration.class)
class CommerceServiceApplicationTests {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final RSAKey SIGNING_KEY = generateRsaKey();
    private static final RSAKey OTHER_SIGNING_KEY = generateRsaKey();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("jwtDecoder")
    private JwtDecoder unconfiguredProductionDecoder;

    @AfterEach
    void clearsCorrelationMdc() {
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void contextStartsWithoutProductionJwtConfiguration() {
        assertThat(unconfiguredProductionDecoder).isNotNull();
    }

    @Test
    void protectedPathWithoutTokenReturnsProblemJson401() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/test")
                        .header(CORRELATION_HEADER, "missing-token-request"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(CORRELATION_HEADER, "missing-token-request"))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andReturn();

        JsonNode problem = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(problem.path("type").asText()).isEqualTo("about:blank");
        assertThat(problem.path("title").asText()).isEqualTo("Unauthorized");
        assertThat(problem.path("status").asInt()).isEqualTo(401);
        assertThat(problem.path("detail").asText()).isEqualTo("Authentication is required");
        assertThat(problem.path("instance").asText()).isEqualTo("/api/test");
        assertThat(problem.path("code").asText()).isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(problem.path("timestamp").asText()).isNotBlank();
        assertThat(problem.path("correlationId").asText()).isEqualTo("missing-token-request");
    }

    @Test
    void validSignedTokenAuthenticatesProtectedRequest() throws Exception {
        mockMvc.perform(get("/api/test").header(HttpHeaders.AUTHORIZATION, bearer(validToken(SIGNING_KEY))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("test-user"));
    }

    @Test
    void expiredTokenReturnsGenericUnauthorizedProblem() throws Exception {
        mockMvc.perform(get("/api/test").header(HttpHeaders.AUTHORIZATION,
                        bearer(token(SIGNING_KEY, Instant.now().minusSeconds(60)))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_BEARER_TOKEN"))
                .andExpect(jsonPath("$.detail").value("Bearer token is invalid"));
    }

    @Test
    void invalidSignatureReturnsGenericUnauthorizedProblem() throws Exception {
        mockMvc.perform(get("/api/test").header(HttpHeaders.AUTHORIZATION, bearer(validToken(OTHER_SIGNING_KEY))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_BEARER_TOKEN"))
                .andExpect(jsonPath("$.detail").value("Bearer token is invalid"));
    }

    @Test
    void productionDecoderRejectsTokensWhenNoJwksUriIsConfigured() {
        assertThatThrownBy(() -> unconfiguredProductionDecoder.decode(validToken(SIGNING_KEY)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void malformedRequestReturnsSanitizedBadRequestProblem() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/test/echo")
                        .header(HttpHeaders.AUTHORIZATION, bearer(validToken(SIGNING_KEY)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{sensitive-request-marker}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.detail").value("Request is missing or malformed"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("JsonParseException", "sensitive-request-marker", ".java:");
    }

    @Test
    void unexpectedFailureReturnsSanitizedServerError() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/test/failure")
                        .header(HttpHeaders.AUTHORIZATION, bearer(validToken(SIGNING_KEY))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("sensitive-internal-marker", "IllegalStateException", "stackTrace");
    }

    @Test
    void generatedCorrelationIdIsReturnedForRequestsWithoutOne() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/test")
                        .header(HttpHeaders.AUTHORIZATION, bearer(validToken(SIGNING_KEY))))
                .andExpect(status().isOk())
                .andExpect(header().exists(CORRELATION_HEADER))
                .andReturn();

        assertThat(result.getResponse().getHeader(CORRELATION_HEADER)).matches("[A-Za-z0-9-]{36}");
    }

    @Test
    void requestLogIncludesSafeFieldsAndNeverLogsAuthorization() throws Exception {
        Logger requestLogger = (Logger) LoggerFactory.getLogger(CorrelationIdFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        requestLogger.addAppender(appender);
        String token = validToken(SIGNING_KEY);
        String logs;
        try {
            mockMvc.perform(get("/api/test")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .header(CORRELATION_HEADER, "safe-log-id"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(CORRELATION_HEADER, "safe-log-id"));
            logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + "\n" + right);
        } finally {
            requestLogger.detachAppender(appender);
            appender.stop();
        }

        assertThat(logs).contains("service=commerce-service", "correlationId=safe-log-id", "method=GET",
                "path=/api/test", "status=200", "durationMs=");
        assertThat(logs).doesNotContain(token, "Authorization");
    }

    @Test
    void openApiIsPublicAndDocumentsBearerJwt() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        JsonNode api = objectMapper.readTree(result.getResponse().getContentAsByteArray());

        assertThat(api.path("info").path("title").asText()).isEqualTo("StyleMind Commerce API");
        assertThat(api.path("info").path("version").asText()).isEqualTo("v1");
        assertThat(api.path("components").path("securitySchemes").path("bearerAuth").path("type").asText())
                .isEqualTo("http");
        assertThat(api.path("components").path("securitySchemes").path("bearerAuth").path("scheme").asText())
                .isEqualTo("bearer");
        assertThat(api.path("components").path("securitySchemes").path("bearerAuth").path("bearerFormat").asText())
                .isEqualTo("JWT");
        assertThat(api.path("paths").has("/api/test")).isFalse();
        assertThat(api.path("paths").has("/auth/login")).isFalse();
    }

    @Test
    void actuatorHealthRemainsPublicAndOnlyHealthIsExposed() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/beans")).andExpect(status().isUnauthorized());
    }

    @Test
    void nonApiRoutesAreDeniedWithProblemJson() throws Exception {
        mockMvc.perform(get("/internal/unconfigured")
                        .header(HttpHeaders.AUTHORIZATION, bearer(validToken(SIGNING_KEY))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private String validToken(RSAKey key) {
        return token(key, Instant.now().plusSeconds(300));
    }

    private String token(RSAKey key, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("test-user")
                .issuedAt(expiresAt.minusSeconds(120))
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.getKeyID()).build();
        JwtEncoderParameters parameters = JwtEncoderParameters.from(header, claims);
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(key)))
                .encode(parameters).getTokenValue();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private static RSAKey generateRsaKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JwtTestConfiguration {

        @Bean
        @org.springframework.context.annotation.Primary
        JwtDecoder testJwtDecoder() throws Exception {
            return NimbusJwtDecoder.withPublicKey(SIGNING_KEY.toRSAPublicKey()).build();
        }

        @Bean
        TestOnlyApiController testOnlyApiController() {
            return new TestOnlyApiController();
        }
    }

    @Hidden
    @RestController
    static class TestOnlyApiController {

        @GetMapping("/api/test")
        Map<String, String> protectedResource(@AuthenticationPrincipal Jwt jwt) {
            return Map.of("subject", jwt.getSubject());
        }

        @GetMapping("/api/test/failure")
        Map<String, String> failure() {
            throw new IllegalStateException("sensitive-internal-marker");
        }

        @PostMapping(value = "/api/test/echo", consumes = MediaType.APPLICATION_JSON_VALUE)
        Map<String, String> echo(@RequestBody Map<String, String> body) {
            return body;
        }
    }
}
