package com.stylemind.identity;

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
import com.stylemind.identity.logging.CorrelationIdFilter;
import com.stylemind.identity.service.address.ManageAddressService;
import com.stylemind.identity.service.address.internal.ManageAddressPersistenceService;
import com.stylemind.identity.service.emailverification.EmailVerificationService;
import com.stylemind.identity.service.emailverification.internal.EmailVerificationPersistence;
import com.stylemind.identity.service.login.LoginService;
import com.stylemind.identity.service.login.internal.LoginSessionService;
import com.stylemind.identity.service.passwordreset.PasswordResetService;
import com.stylemind.identity.service.passwordreset.internal.PasswordResetPersistence;
import com.stylemind.identity.service.passwordreset.internal.PasswordResetTransactionService;
import com.stylemind.identity.service.profile.ManageProfileService;
import com.stylemind.identity.service.profile.internal.ManageProfilePersistenceService;
import com.stylemind.identity.service.register.RegisterService;
import com.stylemind.identity.service.session.RefreshSessionService;
import com.stylemind.identity.service.session.internal.RefreshSessionTransactionService;
import com.stylemind.identity.service.users.ManageUsersService;
import io.swagger.v3.oas.annotations.Hidden;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "management.health.db.enabled=false",
        "spring.datasource.hikari.initialization-fail-timeout=-1"
})
@AutoConfigureMockMvc
@Import(IdentityServiceApplicationTests.JwtTestConfiguration.class)
class IdentityServiceApplicationTests {

    private static final String TEST_HMAC_SECRET = randomSecret();
    private static final String TEST_SERVICE_TOKEN = randomSecret();

    @DynamicPropertySource
    static void verificationSecrets(DynamicPropertyRegistry registry) {
        registry.add("stylemind.verification.hmac-secret", () -> TEST_HMAC_SECRET);
        registry.add("stylemind.notification.service-token", () -> TEST_SERVICE_TOKEN);
        JwtTestSupport.registerProperties(registry);
    }

    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String TEST_SUBJECT = UUID.randomUUID().toString();
    private static final RSAKey SIGNING_KEY = JwtTestSupport.SIGNING_KEY;
    private static final RSAKey OTHER_SIGNING_KEY = JwtTestSupport.generateRsaKey();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    @Qualifier("jwtDecoder")
    private JwtDecoder unconfiguredProductionDecoder;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void clearsCorrelationMdc() {
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void contextLoadsWithStableTestSigningKey() {
        assertThat(unconfiguredProductionDecoder).isNotNull();
    }

    @Test
    void businessServicesHaveSingleInterfaceBeansAndTransactionsRemainProxied() {
        assertThat(applicationContext.getBeansOfType(RegisterService.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(EmailVerificationService.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(LoginService.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(RefreshSessionService.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(PasswordResetService.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(ManageProfileService.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(ManageAddressService.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(ManageUsersService.class)).hasSize(1);

        assertThat(AopUtils.isAopProxy(applicationContext.getBean(EmailVerificationPersistence.class))).isTrue();
        assertThat(AopUtils.isAopProxy(applicationContext.getBean(LoginSessionService.class))).isTrue();
        assertThat(AopUtils.isAopProxy(applicationContext.getBean(RefreshSessionTransactionService.class))).isTrue();
        assertThat(AopUtils.isAopProxy(applicationContext.getBean(PasswordResetPersistence.class))).isTrue();
        assertThat(AopUtils.isAopProxy(applicationContext.getBean(PasswordResetTransactionService.class))).isTrue();
        assertThat(AopUtils.isAopProxy(applicationContext.getBean(ManageProfilePersistenceService.class))).isTrue();
        assertThat(AopUtils.isAopProxy(applicationContext.getBean(ManageAddressPersistenceService.class))).isTrue();
        assertThat(AopUtils.isAopProxy(applicationContext.getBean(ManageUsersService.class))).isTrue();
    }

    @Test
    void passwordEncoderStoresOnlyAOneWayEncodedValue() {
        String password = "Register-only-test-password";

        String encoded = passwordEncoder.encode(password);

        assertThat(encoded).startsWith("{bcrypt}").doesNotContain(password);
        assertThat(passwordEncoder.matches(password, encoded)).isTrue();
        assertThat(passwordEncoder.matches("wrong-password", encoded)).isFalse();
    }

    @Test
    void registerValidationReturnsSanitizedProblemDetails() throws Exception {
        String passwordMarker = "sensitive-register-password-marker";

        assertInvalidRegister(Map.of("email", "", "password", passwordMarker), passwordMarker);
        assertInvalidRegister(Map.of("password", passwordMarker), passwordMarker);
        assertInvalidRegister(Map.of("email", "not-an-email", "password", passwordMarker), passwordMarker);
        assertInvalidRegister(Map.of("email", "customer@example.com", "password", "   "), "   ");
        assertInvalidRegister(Map.of("email", "customer@example.com", "password", "short"), "short");
        assertInvalidRegister(Map.of("email", "customer@example.com", "password", "p".repeat(73)), "p".repeat(73));
        assertInvalidRegister(Map.of("email", "customer@example.com", "password", "€".repeat(25)), "€".repeat(25));

        Map<String, Object> missingPassword = new LinkedHashMap<>();
        missingPassword.put("email", "customer@example.com");
        assertInvalidRegister(missingPassword, null);
    }

    @Test
    void onlyPostRegisterIsPublic() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/v1/auth/register"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(post("/api/v1/auth/register/extra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/v1/auth/verify-email"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(post("/api/v1/auth/resend-email-verification/extra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(post("/api/v1/auth/login/extra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist())
                .andExpect(jsonPath("$.keys[0].p").doesNotExist())
                .andExpect(jsonPath("$.keys[0].q").doesNotExist());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/.well-known/jwks.json"))
                .andExpect(status().isUnauthorized());
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
                .andExpect(jsonPath("$.subject").value(TEST_SUBJECT));
    }

    @Test
    void rolesClaimMapsToAuthoritiesAndMissingRolesGrantNothing() throws Exception {
        JwtClaimsSet withRoles = validClaims(Instant.now().plusSeconds(300), List.of("CUSTOMER", "ADMIN"));
        mockMvc.perform(get("/api/test/authorities")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token(SIGNING_KEY, withRoles))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$[1]").value("ROLE_CUSTOMER"));

        JwtClaimsSet withoutRoles = validClaims(Instant.now().plusSeconds(300), null);
        mockMvc.perform(get("/api/test/authorities")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token(SIGNING_KEY, withoutRoles))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void decoderRejectsWrongIssuerAudienceSubjectAndMissingExpiry() throws Exception {
        assertInvalidBearer(validClaims(Instant.now().plusSeconds(300), List.of("CUSTOMER"),
                "https://wrong-issuer.stylemind.local", JwtTestSupport.AUDIENCE, TEST_SUBJECT));
        assertInvalidBearer(validClaims(Instant.now().plusSeconds(300), List.of("CUSTOMER"),
                JwtTestSupport.ISSUER, "wrong-audience", TEST_SUBJECT));
        assertInvalidBearer(validClaims(Instant.now().plusSeconds(300), List.of("CUSTOMER"),
                JwtTestSupport.ISSUER, JwtTestSupport.AUDIENCE, "not-a-uuid"));

        JwtClaimsSet missingExpiry = JwtClaimsSet.builder()
                .issuer(JwtTestSupport.ISSUER)
                .audience(List.of(JwtTestSupport.AUDIENCE))
                .subject(TEST_SUBJECT)
                .issuedAt(Instant.now().minusSeconds(5))
                .id(UUID.randomUUID().toString())
                .build();
        assertInvalidBearer(missingExpiry);

        JwtClaimsSet futureIssuedAt = JwtClaimsSet.builder()
                .issuer(JwtTestSupport.ISSUER)
                .audience(List.of(JwtTestSupport.AUDIENCE))
                .subject(TEST_SUBJECT)
                .issuedAt(Instant.now().plusSeconds(120))
                .expiresAt(Instant.now().plusSeconds(300))
                .id(UUID.randomUUID().toString())
                .build();
        assertInvalidBearer(futureIssuedAt);

        String wrongAlgorithm = token(SIGNING_KEY, validClaims(Instant.now().plusSeconds(300), List.of("CUSTOMER")),
                SignatureAlgorithm.RS384);
        mockMvc.perform(get("/api/test").header(HttpHeaders.AUTHORIZATION, bearer(wrongAlgorithm)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_BEARER_TOKEN"));
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
    void productionDecoderUsesConfiguredLocalSigningKey() {
        assertThat(unconfiguredProductionDecoder.decode(validToken(SIGNING_KEY)).getSubject()).isNotBlank();
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

        assertThat(logs).contains("service=identity-service", "correlationId=safe-log-id", "method=GET",
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

        assertThat(api.path("info").path("title").asText()).isEqualTo("StyleMind Identity API");
        assertThat(api.path("info").path("version").asText()).isEqualTo("v1");
        assertThat(api.path("components").path("securitySchemes").path("bearerAuth").path("type").asText())
                .isEqualTo("http");
        assertThat(api.path("components").path("securitySchemes").path("bearerAuth").path("scheme").asText())
                .isEqualTo("bearer");
        assertThat(api.path("components").path("securitySchemes").path("bearerAuth").path("bearerFormat").asText())
                .isEqualTo("JWT");
        assertThat(api.path("paths").has("/api/test")).isFalse();
        JsonNode login = api.path("paths").path("/api/v1/auth/login").path("post");
        assertThat(login.isObject()).isTrue();
        assertThat(login.path("responses").has("200")).isTrue();
        assertThat(login.path("responses").has("401")).isTrue();
        assertThat(login.path("security")).isEmpty();
        assertThat(api.path("paths").path("/.well-known/jwks.json").path("get").isObject()).isTrue();
        JsonNode register = api.path("paths").path("/api/v1/auth/register").path("post");
        assertThat(register.path("responses").has("201")).isTrue();
        assertThat(register.path("responses").has("400")).isTrue();
        assertThat(register.path("responses").has("409")).isTrue();
        assertThat(register.path("security").isArray()).isTrue();
        assertThat(register.path("security")).isEmpty();
        assertThat(api.path("paths").path("/api/v1/auth/verify-email").path("post").path("security")).isEmpty();
        assertThat(api.path("paths").path("/api/v1/auth/resend-email-verification").path("post")
                .path("security")).isEmpty();
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
        mockMvc.perform(get("/unconfigured")
                        .header(HttpHeaders.AUTHORIZATION, bearer(validToken(SIGNING_KEY))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private String validToken(RSAKey key) {
        return token(key, Instant.now().plusSeconds(300));
    }

    private String token(RSAKey key, Instant expiresAt) {
        return token(key, validClaims(expiresAt, List.of("CUSTOMER")));
    }

    private JwtClaimsSet validClaims(Instant expiresAt, List<String> roles) {
        return validClaims(expiresAt, roles, JwtTestSupport.ISSUER, JwtTestSupport.AUDIENCE, TEST_SUBJECT);
    }

    private JwtClaimsSet validClaims(
            Instant expiresAt, List<String> roles, String issuer, String audience, String subject) {
        Instant issuedAt = expiresAt.isBefore(Instant.now())
                ? expiresAt.minusSeconds(120)
                : Instant.now().minusSeconds(2);
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString());
        if (roles != null) {
            builder.claim("roles", roles);
        }
        return builder.build();
    }

    private void assertInvalidBearer(JwtClaimsSet claims) throws Exception {
        mockMvc.perform(get("/api/test")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token(SIGNING_KEY, claims))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_BEARER_TOKEN"));
    }

    private String token(RSAKey key, JwtClaimsSet claims) {
        return token(key, claims, SignatureAlgorithm.RS256);
    }

    private String token(RSAKey key, JwtClaimsSet claims, SignatureAlgorithm algorithm) {
        JwsHeader header = JwsHeader.with(algorithm).keyId(key.getKeyID()).build();
        JwtEncoderParameters parameters = JwtEncoderParameters.from(header, claims);
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(key)))
                .encode(parameters).getTokenValue();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private void assertInvalidRegister(Map<String, ?> request, String rejectedValue) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andReturn();
        String response = result.getResponse().getContentAsString();
        assertThat(response).doesNotContain("password_hash", "password", "constraint", "SQL");
        if (rejectedValue != null) {
            assertThat(response).doesNotContain(rejectedValue);
        }
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JwtTestConfiguration {

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

        @GetMapping("/api/test/authorities")
        List<String> authorities(org.springframework.security.core.Authentication authentication) {
            return authentication.getAuthorities().stream()
                    .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                    .sorted()
                    .toList();
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
