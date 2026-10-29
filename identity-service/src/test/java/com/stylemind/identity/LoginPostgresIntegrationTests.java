package com.stylemind.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stylemind.identity.entity.Account;
import com.stylemind.identity.errors.handler.ApiExceptionHandler;
import com.stylemind.identity.logging.CorrelationIdFilter;
import com.stylemind.identity.repository.AccountRepository;
import com.stylemind.identity.repository.RefreshSessionRepository;
import com.stylemind.identity.security.jwt.JwtTokenService;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.health.db.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
@Import(RepositoryCallHooks.ConfigurationForTests.class)
class LoginPostgresIntegrationTests {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6")
            .withDatabaseName("stylemind_identity_login_test")
            .withUsername("identity_login_test")
            .withPassword("identity_login_test_only");
    private static final String HMAC_SECRET = JwtTestSupport.randomSecret();
    private static final String SERVICE_TOKEN = JwtTestSupport.randomSecret();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES_CONTAINER = POSTGRES;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("stylemind.verification.hmac-secret", () -> HMAC_SECRET);
        registry.add("stylemind.notification.service-token", () -> SERVICE_TOKEN);
        JwtTestSupport.registerProperties(registry);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtDecoder jwtDecoder;
    @LocalServerPort private int serverPort;

    @MockitoSpyBean private JwtTokenService jwtTokenService;

    @BeforeEach
    void clearDisposableIdentityDatabase() {
        jdbcTemplate.update("DELETE FROM public.email_verification_challenge");
        jdbcTemplate.update("DELETE FROM public.refresh_session");
        jdbcTemplate.update("DELETE FROM public.account_role");
        jdbcTemplate.update("DELETE FROM public.account");
    }

    @Test
    void loginIssuesRealAccessAndOpaqueRefreshTokensAndPersistsOnlyDigest() throws Exception {
        String password = "  password kept exactly  ";
        AccountFixture account = createAccount("ACTIVE", true, password, "CUSTOMER", "ADMIN");
        MvcResult result = postLogin("  " + account.email().toUpperCase(java.util.Locale.ROOT) + "  ", password)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(900))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        String accessToken = response.path("access_token").asText();
        String refreshToken = response.path("refresh_token").asText();
        assertThat(response.size()).isEqualTo(4);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(password, "password_hash", "email_verified_at");
        assertThat(Base64.getUrlDecoder().decode(refreshToken)).hasSize(32);

        Jwt jwt = jwtDecoder.decode(accessToken);
        Jwt consumerDecoded = NimbusJwtDecoder.withJwkSetUri(
                "http://localhost:" + serverPort + "/.well-known/jwks.json")
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build()
                .decode(accessToken);
        assertThat(consumerDecoded.getSubject()).isEqualTo(account.accountId().toString());
        assertThat(jwt.getSubject()).isEqualTo(account.accountId().toString());
        assertThat(jwt.getIssuer().toString()).isEqualTo(JwtTestSupport.ISSUER);
        assertThat(jwt.getAudience()).containsExactly(JwtTestSupport.AUDIENCE);
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isNotNull();
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).toSeconds()).isEqualTo(900);
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN", "CUSTOMER");
        SignedJWT parsed = SignedJWT.parse(accessToken);
        assertThat(parsed.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(parsed.getHeader().getKeyID()).isNotBlank();

        Map<String, Object> session = jdbcTemplate.queryForMap("""
                SELECT session_id, account_id, token_hash, issued_at, expires_at, revoked_at
                FROM public.refresh_session WHERE account_id = ?
                """, account.accountId());
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(refreshToken.getBytes(StandardCharsets.US_ASCII)));
        assertThat(session.get("account_id")).isEqualTo(account.accountId());
        assertThat(session.get("token_hash")).isEqualTo(digest).isNotEqualTo(refreshToken);
        assertThat(session.get("revoked_at")).isNull();
        java.time.Instant issuedAt = ((java.sql.Timestamp) session.get("issued_at")).toInstant();
        java.time.Instant refreshExpiresAt = ((java.sql.Timestamp) session.get("expires_at")).toInstant();
        assertThat(Duration.between(issuedAt, refreshExpiresAt).toDays()).isEqualTo(7);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.refresh_session WHERE token_hash = ?", Integer.class, refreshToken))
                .isZero();

        JsonNode jwks = objectMapper.readTree(mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        JsonNode publicKey = jwks.path("keys").get(0);
        assertThat(publicKey.path("kid").asText()).isEqualTo(parsed.getHeader().getKeyID());
        assertThat(publicKey.path("kty").asText()).isEqualTo("RSA");
        for (String privateParameter : List.of("d", "p", "q", "dp", "dq", "qi")) {
            assertThat(publicKey.has(privateParameter)).isFalse();
        }
    }

    @Test
    void unknownEmailAndWrongPasswordShareTheSameCredentialError() throws Exception {
        AccountFixture account = createAccount("ACTIVE", true, "  Correct login password  ", "CUSTOMER");
        MvcResult wrongPassword = postLogin(account.email(), "wrong password").andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS")).andReturn();
        MvcResult unknownEmail = postLogin("unknown-" + UUID.randomUUID() + "@example.test", "wrong password")
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS")).andReturn();

        JsonNode wrongProblem = objectMapper.readTree(wrongPassword.getResponse().getContentAsByteArray());
        JsonNode unknownProblem = objectMapper.readTree(unknownEmail.getResponse().getContentAsByteArray());
        assertThat(wrongProblem.path("detail").asText()).isEqualTo("Email or password is invalid");
        assertThat(unknownProblem.path("detail").asText()).isEqualTo(wrongProblem.path("detail").asText());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.refresh_session", Integer.class)).isZero();
    }

    @Test
    void passwordWhitespaceIsPreservedAndCreationPolicyIsNotReapplied() throws Exception {
        String password = "  valid register password  ";
        AccountFixture account = createAccount("ACTIVE", true, password, "CUSTOMER");

        postLogin(account.email(), password).andExpect(status().isOk());
        postLogin(account.email(), password.trim())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.refresh_session", Integer.class)).isEqualTo(1);

        AccountFixture shortPasswordAccount = createAccount("ACTIVE", true, "short", "CUSTOMER");
        postLogin(shortPasswordAccount.email(), "short").andExpect(status().isOk());
    }

    @Test
    void otpChallengeLockDoesNotBlockAnOtherwiseEligibleLogin() throws Exception {
        AccountFixture account = createAccount("ACTIVE", true, "Correct account password", "CUSTOMER");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update("""
                INSERT INTO public.email_verification_challenge (
                    challenge_id, account_id, code_digest, expires_at, failed_attempts, consumed_at,
                    last_send_attempt_at, send_window_started_at, send_count, locked_until
                ) VALUES (?, ?, ?, ?, 5, NULL, ?, ?, 1, ?)
                """, UUID.randomUUID(), account.accountId(), new byte[32], now.plusMinutes(5),
                now, now, now.plusMinutes(15));

        postLogin(account.email(), "Correct account password").andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.refresh_session WHERE account_id = ?",
                Integer.class, account.accountId())).isEqualTo(1);
    }

    @Test
    void exactly72Utf8BytesCanBeCheckedAnd73BytesAreRejectedAsInput() throws Exception {
        String passwordAtLimit = "€".repeat(24);
        AccountFixture account = createAccount("ACTIVE", true, passwordAtLimit, "CUSTOMER");
        postLogin(account.email(), passwordAtLimit).andExpect(status().isOk());
        postLogin(account.email(), "a".repeat(73)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void invalidInputAndUnexpectedFieldsReturnSanitizedBadRequest() throws Exception {
        postLogin("not-an-email", "password").andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        postLogin("user@example.test", "   ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        postLogin("user@example.test", "€".repeat(25)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        MvcResult unexpected = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.test\",\"password\":\"no-secret-output\","
                                + "\"role\":\"ADMIN\",\"account_status\":\"ACTIVE\","
                                + "\"account_id\":\"00000000-0000-4000-8000-000000000001\","
                                + "\"roles\":[\"ADMIN\"],\"sub\":\"forged\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andReturn();
        assertThat(unexpected.getResponse().getContentAsString()).doesNotContain("no-secret-output", "ADMIN");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.refresh_session", Integer.class)).isZero();
    }

    @Test
    void pendingLockedAndUnverifiedAccountsAreDeniedOnlyAfterCorrectPassword() throws Exception {
        for (String accountStatus : List.of("PENDING_VERIFICATION", "LOCKED")) {
            AccountFixture account = createAccount(accountStatus, true, "Correct account password", "CUSTOMER");
            postLogin(account.email(), "incorrect account password")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
            postLogin(account.email(), "Correct account password")
                    .andExpect(status().isForbidden())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));
        }
        AccountFixture unverified = createAccount("ACTIVE", false, "Correct account password", "CUSTOMER");
        postLogin(unverified.email(), "Correct account password")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.refresh_session", Integer.class)).isZero();
    }

    @Test
    void accountWithoutDatabaseRoleCannotReceiveTokens() throws Exception {
        AccountFixture account = createAccount("ACTIVE", true, "Correct account password");
        postLogin(account.email(), "Correct account password")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.refresh_session", Integer.class)).isZero();
    }

    @Test
    void statusChangedAfterCredentialCheckIsRevalidatedUnderLock() throws Exception {
        AccountFixture account = createAccount("ACTIVE", true, "Correct account password", "CUSTOMER");
        try (AutoCloseable hook = RepositoryCallHooks.install((bean, method, arguments, invocation) -> {
            Object result = invocation.proceed();
            if ("accountRepository".equals(bean) && "findByEmail".equals(method.getName())
                    && account.email().equals(arguments[0])) {
                jdbcTemplate.update("UPDATE public.account SET account_status = 'LOCKED' WHERE account_id = ?",
                        account.accountId());
            }
            return result;
        })) {
            postLogin(account.email(), "Correct account password")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.refresh_session", Integer.class)).isZero();
    }

    @Test
    void passwordHashChangedAfterCredentialCheckIsRejectedWithoutSession() throws Exception {
        AccountFixture account = createAccount("ACTIVE", true, "Original correct password", "CUSTOMER");
        try (AutoCloseable hook = RepositoryCallHooks.install((bean, method, arguments, invocation) -> {
            Object result = invocation.proceed();
            if ("accountRepository".equals(bean) && "findByEmail".equals(method.getName())
                    && account.email().equals(arguments[0])) {
                jdbcTemplate.update("UPDATE public.account SET password_hash = ? WHERE account_id = ?",
                        passwordEncoder.encode("new password value"), account.accountId());
            }
            return result;
        })) {
            postLogin(account.email(), "Original correct password")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.refresh_session", Integer.class)).isZero();
    }

    @Test
    void separateLoginsCreateIndependentSessionsAndDoNotRevokePreviousSession() throws Exception {
        AccountFixture account = createAccount("ACTIVE", true, "Correct account password", "CUSTOMER");
        JsonNode first = login(account.email(), "Correct account password");
        JsonNode second = login(account.email(), "Correct account password");
        String firstRefresh = first.path("refresh_token").asText();
        String secondRefresh = second.path("refresh_token").asText();

        assertThat(firstRefresh).isNotEqualTo(secondRefresh);
        assertThat(first.path("access_token").asText()).isNotEqualTo(second.path("access_token").asText());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.refresh_session WHERE account_id = ?",
                Integer.class, account.accountId())).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.refresh_session WHERE account_id = ? AND revoked_at IS NULL",
                Integer.class, account.accountId())).isEqualTo(2);
    }

    @Test
    void signingFailureAndSessionInsertFailureReturnNoTokensAndLeaveNoSession() throws Exception {
        AccountFixture account = createAccount("ACTIVE", true, "Correct account password", "CUSTOMER");
        doThrow(new IllegalStateException("test-only signing failure"))
                .when(jwtTokenService).issue(any(UUID.class), anyList());
        MvcResult signingFailure = postLogin(account.email(), "Correct account password")
                .andExpect(status().isInternalServerError()).andReturn();
        assertThat(signingFailure.getResponse().getContentAsString()).doesNotContain("access_token", "refresh_token");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.refresh_session", Integer.class)).isZero();

        org.mockito.Mockito.reset(jwtTokenService);
        MvcResult insertFailure;
        try (AutoCloseable hook = RepositoryCallHooks.install((bean, method, arguments, invocation) -> {
            Object result = invocation.proceed();
            if ("refreshSessionRepository".equals(bean) && "save".equals(method.getName())) {
                throw new IllegalStateException("test-only session write failure");
            }
            return result;
        })) {
            insertFailure = postLogin(account.email(), "Correct account password")
                    .andExpect(status().isInternalServerError()).andReturn();
        }
        assertThat(insertFailure.getResponse().getContentAsString()).doesNotContain("access_token", "refresh_token");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.refresh_session", Integer.class)).isZero();
    }

    @Test
    void corruptPasswordHashFailsClosedWithoutDisclosingStoredValue() throws Exception {
        AccountFixture account = createAccount("ACTIVE", true, "Correct account password", "CUSTOMER");
        String corruptHash = "{unknown}stored-hash-marker";
        String passwordMarker = "sensitive-login-password-marker";
        jdbcTemplate.update("UPDATE public.account SET password_hash = ? WHERE account_id = ?",
                corruptHash, account.accountId());

        Logger requestLogger = (Logger) LoggerFactory.getLogger(CorrelationIdFilter.class);
        Logger errorLogger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        requestLogger.addAppender(appender);
        errorLogger.addAppender(appender);
        MvcResult result;
        String logs;
        try {
            result = postLogin(account.email(), passwordMarker)
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                    .andReturn();
            logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + "\n" + right);
        } finally {
            requestLogger.detachAppender(appender);
            errorLogger.detachAppender(appender);
            appender.stop();
        }
        assertThat(result.getResponse().getContentAsString()).doesNotContain(corruptHash, "password_hash");
        assertThat(logs).doesNotContain(corruptHash, passwordMarker);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.refresh_session", Integer.class)).isZero();
    }

    private ResultActions postLogin(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("email", email, "password", password))));
    }

    private JsonNode login(String email, String password) throws Exception {
        MvcResult result = postLogin(email, password).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private AccountFixture createAccount(String status, boolean verified, String password, String... roles) {
        UUID accountId = UUID.randomUUID();
        String email = "login-" + UUID.randomUUID() + "@example.test";
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update("""
                INSERT INTO public.account (
                    account_id, email, password_hash, account_status, email_verified_at, registered_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, accountId, email, passwordEncoder.encode(password), status,
                verified ? now : null, now);
        for (String role : roles) {
            jdbcTemplate.update("INSERT INTO public.account_role (account_id, role_code) VALUES (?, ?)",
                    accountId, role);
        }
        return new AccountFixture(accountId, email);
    }

    private record AccountFixture(UUID accountId, String email) {
    }
}
