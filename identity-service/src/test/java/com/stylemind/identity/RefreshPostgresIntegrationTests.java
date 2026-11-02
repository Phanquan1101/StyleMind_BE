package com.stylemind.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.identity.errors.ApiExceptionHandler;
import com.stylemind.identity.logging.CorrelationIdFilter;
import com.stylemind.identity.repository.session.RefreshSessionRepository;
import com.stylemind.identity.entity.RefreshSessionRow;
import com.stylemind.identity.security.JwtTokenService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.health.db.enabled=false")
@Testcontainers
@Import(RefreshPostgresIntegrationTests.TestClockConfiguration.class)
class RefreshPostgresIntegrationTests {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6")
            .withDatabaseName("stylemind_identity_refresh_test")
            .withUsername("identity_refresh_test")
            .withPassword("identity_refresh_test_only");
    private static final String HMAC_SECRET = JwtTestSupport.randomSecret();
    private static final String SERVICE_TOKEN = JwtTestSupport.randomSecret();
    private static final MutableClock TEST_CLOCK = new MutableClock(Instant.now());
    private static final String PASSWORD = "Refresh test password";
    private static final String EMAIL_DOMAIN = "@refresh.example.test";

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

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtDecoder jwtDecoder;
    @LocalServerPort private int serverPort;
    @MockitoSpyBean private RefreshSessionRepository refreshSessionRepository;
    @MockitoSpyBean private JwtTokenService jwtTokenService;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void clearDisposableDatabaseAndSetClock() {
        jdbcTemplate.update("DELETE FROM public.email_verification_challenge");
        jdbcTemplate.update("DELETE FROM public.refresh_session");
        jdbcTemplate.update("DELETE FROM public.account_role");
        jdbcTemplate.update("DELETE FROM public.account");
        TEST_CLOCK.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
    }

    @Test
    void loginRefreshUseNewAccessThenLogoutLeavesAccessJwtValidUntilItsExpiry() throws Exception {
        AccountFixture account = createAccount("ACTIVE", true, "CUSTOMER");
        JsonNode login = login(account.email());
        String oldRefreshToken = login.path("refresh_token").asText();
        String originalAccessToken = login.path("access_token").asText();
        String oldHash = hash(oldRefreshToken);
        OffsetDateTime originalExpiry = expiresAt(oldHash);

        JsonNode refreshed = successfulRefresh(oldRefreshToken);
        String newRefreshToken = refreshed.path("refresh_token").asText();
        String newAccessToken = refreshed.path("access_token").asText();
        String newHash = hash(newRefreshToken);
        assertThat(newRefreshToken).isNotEqualTo(oldRefreshToken);
        assertThat(Base64.getUrlDecoder().decode(newRefreshToken)).hasSize(32);
        assertThat(refreshed.path("token_type").asText()).isEqualTo("Bearer");
        assertThat(refreshed.path("expires_in").asLong()).isEqualTo(900);
        assertThat(jdbcTemplate.queryForObject("SELECT token_hash FROM public.refresh_session WHERE token_hash = ?",
                String.class, newHash)).isEqualTo(newHash);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.refresh_session WHERE token_hash = ?",
                Integer.class, newRefreshToken)).isZero();
        assertThat(refreshed.toString()).doesNotContain("token_hash", newHash);
        assertThat(expiresAt(newHash)).isEqualTo(originalExpiry);
        assertThat(revokedAt(oldHash)).isNotNull();

        Jwt newAccess = jwtDecoder.decode(newAccessToken);
        assertThat(newAccess.getSubject()).isEqualTo(account.accountId().toString());
        assertThat(newAccess.getIssuer().toString()).isEqualTo(JwtTestSupport.ISSUER);
        assertThat(newAccess.getAudience()).containsExactly(JwtTestSupport.AUDIENCE);
        assertThat(newAccess.getClaimAsStringList("roles")).containsExactly("CUSTOMER");
        assertThat(DurationSeconds.between(newAccess.getIssuedAt(), newAccess.getExpiresAt())).isEqualTo(900);
        Jwt jwksDecoded = NimbusJwtDecoder.withJwkSetUri("http://localhost:" + serverPort + "/.well-known/jwks.json")
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build()
                .decode(newAccessToken);
        assertThat(jwksDecoded.getSubject()).isEqualTo(account.accountId().toString());

        Jwt originalAccess = jwtDecoder.decode(originalAccessToken);
        assertThat(originalAccess.getSubject()).isEqualTo(account.accountId().toString());
        HttpResponse<String> logout = post("/api/v1/auth/logout", Map.of("refresh_token", newRefreshToken));
        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(logout.headers().firstValue(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        assertThat(logout.body()).isEmpty();
        assertThat(jwtDecoder.decode(originalAccessToken).getSubject()).isEqualTo(account.accountId().toString());
        assertRefreshRejected(newRefreshToken);
    }

    @Test
    void rotationKeepsAbsoluteExpiryAndASeparateAccessTokenLifetime() throws Exception {
        AccountFixture account = createAccount("ACTIVE", true, "CUSTOMER");
        String refreshToken = login(account.email()).path("refresh_token").asText();
        String oldHash = hash(refreshToken);
        OffsetDateTime absoluteExpiry = OffsetDateTime.ofInstant(TEST_CLOCK.instant().plusSeconds(5), ZoneOffset.UTC);
        jdbcTemplate.update("UPDATE public.refresh_session SET expires_at = ? WHERE token_hash = ?",
                absoluteExpiry, oldHash);
        TEST_CLOCK.set(absoluteExpiry.toInstant().minusSeconds(1));

        JsonNode response = successfulRefresh(refreshToken);
        String nextToken = response.path("refresh_token").asText();
        String nextHash = hash(nextToken);
        assertThat(expiresAt(nextHash)).isEqualTo(absoluteExpiry);
        assertThat(issuedAt(nextHash)).isEqualTo(OffsetDateTime.ofInstant(TEST_CLOCK.instant(), ZoneOffset.UTC));
        assertThat(response.path("expires_in").asLong()).isEqualTo(900);
        Jwt access = jwtDecoder.decode(response.path("access_token").asText());
        assertThat(DurationSeconds.between(access.getIssuedAt(), access.getExpiresAt())).isEqualTo(900);

        TEST_CLOCK.set(absoluteExpiry.toInstant());
        assertRefreshRejected(nextToken);
        assertThat(jwtDecoder.decode(response.path("access_token").asText()).getSubject())
                .isEqualTo(account.accountId().toString());
    }

    @Test
    void unknownExpiredRevokedAndReplayedTokensShareOneUnauthorizedResponse() throws Exception {
        AccountFixture account = createAccount("ACTIVE", true, "CUSTOMER");
        String original = login(account.email()).path("refresh_token").asText();
        JsonNode rotated = successfulRefresh(original);
        String current = rotated.path("refresh_token").asText();
        HttpResponse<String> replay = post("/api/v1/auth/refresh", Map.of("refresh_token", original));
        assertInvalidRefresh(replay);

        String unknownToken = randomRefreshToken();
        HttpResponse<String> unknown = post("/api/v1/auth/refresh", Map.of("refresh_token", unknownToken));
        assertInvalidRefresh(unknown);
        assertSameInvalidRefreshProblem(unknown, replay);

        String currentHash = hash(current);
        TEST_CLOCK.set(expiresAt(currentHash).toInstant());
        HttpResponse<String> expired = post("/api/v1/auth/refresh", Map.of("refresh_token", current));
        assertInvalidRefresh(expired);
        assertSameInvalidRefreshProblem(expired, replay);
    }

    @Test
    void malformedInputAndAccessJwtAreRejectedWithoutEchoingCredentials() throws Exception {
        String marker = "refresh-token-secret-marker";
        HttpResponse<String> malformed = post("/api/v1/auth/refresh", Map.of("refresh_token", marker));
        assertBadRequest(malformed);
        assertThat(malformed.body()).doesNotContain(marker);
        assertThat(malformed.headers().firstValue(HttpHeaders.CACHE_CONTROL)).contains("no-store");

        HttpResponse<String> missing = post("/api/v1/auth/refresh", Map.of());
        assertBadRequest(missing);
        HttpResponse<String> unexpected = post("/api/v1/auth/logout",
                Map.of("refresh_token", randomRefreshToken(), "account_id", UUID.randomUUID().toString()));
        assertBadRequest(unexpected);

        AccountFixture account = createAccount("ACTIVE", true, "CUSTOMER");
        String accessToken = login(account.email()).path("access_token").asText();
        HttpResponse<String> accessAsRefresh = post("/api/v1/auth/refresh", Map.of("refresh_token", accessToken));
        assertBadRequest(accessAsRefresh);
        assertThat(accessAsRefresh.body()).doesNotContain(accessToken);
    }

    @Test
    void rejectedRefreshTokenAndDigestAreNeverLoggedOrReturned() throws Exception {
        String token = randomRefreshToken();
        String digest = hash(token);
        Logger requestLogger = (Logger) LoggerFactory.getLogger(CorrelationIdFilter.class);
        Logger errorLogger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        requestLogger.addAppender(appender);
        errorLogger.addAppender(appender);
        HttpResponse<String> response;
        String capturedLogs;
        try {
            response = post("/api/v1/auth/refresh", Map.of("refresh_token", token));
            capturedLogs = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + "\n" + right);
        } finally {
            requestLogger.detachAppender(appender);
            errorLogger.detachAppender(appender);
            appender.stop();
        }
        assertInvalidRefresh(response);
        assertThat(response.body()).doesNotContain(token, digest);
        assertThat(capturedLogs).doesNotContain(token, digest);
    }

    @Test
    void refreshChecksCurrentAccountStateVerificationAndRoleWithoutRevokingOldSession() throws Exception {
        for (String status : List.of("PENDING_VERIFICATION", "LOCKED")) {
            AccountFixture account = createAccount("ACTIVE", true, "CUSTOMER");
            String token = login(account.email()).path("refresh_token").asText();
            jdbcTemplate.update("UPDATE public.account SET account_status = ? WHERE account_id = ?",
                    status, account.accountId());
            assertAccountNotActive(post("/api/v1/auth/refresh", Map.of("refresh_token", token)));
            assertThat(revokedAt(hash(token))).isNull();
        }

        AccountFixture unverified = createAccount("ACTIVE", true, "CUSTOMER");
        String unverifiedToken = login(unverified.email()).path("refresh_token").asText();
        jdbcTemplate.update("UPDATE public.account SET email_verified_at = NULL WHERE account_id = ?",
                unverified.accountId());
        assertAccountNotActive(post("/api/v1/auth/refresh", Map.of("refresh_token", unverifiedToken)));
        assertThat(revokedAt(hash(unverifiedToken))).isNull();

        AccountFixture roleless = createAccount("ACTIVE", true, "CUSTOMER");
        String rolelessToken = login(roleless.email()).path("refresh_token").asText();
        jdbcTemplate.update("DELETE FROM public.account_role WHERE account_id = ?", roleless.accountId());
        assertAccountNotActive(post("/api/v1/auth/refresh", Map.of("refresh_token", rolelessToken)));
        assertThat(revokedAt(hash(rolelessToken))).isNull();
    }

    @Test
    void refreshedJwtUsesCurrentDatabaseRolesAndOtherSessionsRemainUnchanged() throws Exception {
        AccountFixture account = createAccount("ACTIVE", true, "CUSTOMER");
        JsonNode firstLogin = login(account.email());
        JsonNode unrelatedLogin = login(account.email());
        String tokenToRotate = firstLogin.path("refresh_token").asText();
        String unrelatedHash = hash(unrelatedLogin.path("refresh_token").asText());
        jdbcTemplate.update("DELETE FROM public.account_role WHERE account_id = ?", account.accountId());
        jdbcTemplate.update("INSERT INTO public.account_role (account_id, role_code) VALUES (?, 'ADMIN')",
                account.accountId());

        JsonNode refreshed = successfulRefresh(tokenToRotate);
        Jwt jwt = jwtDecoder.decode(refreshed.path("access_token").asText());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN");
        assertThat(revokedAt(unrelatedHash)).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.refresh_session "
                + "WHERE account_id = ? AND revoked_at IS NULL", Integer.class, account.accountId())).isEqualTo(2);
    }

    @Test
    void signingAndInsertFailuresRollBackRotationAndNeverReturnTokens() throws Exception {
        AccountFixture account = createAccount("ACTIVE", true, "CUSTOMER");
        String token = login(account.email()).path("refresh_token").asText();
        String hash = hash(token);
        doThrow(new IllegalStateException("test-only signing failure"))
                .when(jwtTokenService).rotate(any(UUID.class), anyList(), any(OffsetDateTime.class));
        HttpResponse<String> signingFailure = post("/api/v1/auth/refresh", Map.of("refresh_token", token));
        assertThat(signingFailure.statusCode()).isEqualTo(500);
        assertThat(signingFailure.body()).doesNotContain("access_token", "refresh_token", token);
        assertThat(revokedAt(hash)).isNull();
        assertThat(countSessions(account.accountId())).isEqualTo(1);

        org.mockito.Mockito.reset(jwtTokenService);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("test-only refresh-session insert failure");
        }).when(refreshSessionRepository).saveAndFlush(any(RefreshSessionRow.class));
        HttpResponse<String> insertFailure = post("/api/v1/auth/refresh", Map.of("refresh_token", token));
        assertThat(insertFailure.statusCode()).isEqualTo(500);
        assertThat(insertFailure.body()).doesNotContain("access_token", "refresh_token", token);
        assertThat(revokedAt(hash)).isNull();
        assertThat(countSessions(account.accountId())).isEqualTo(1);
    }

    @Test
    void logoutIsIdempotentWorksForExpiredOrInactiveAccountAndOnlyTouchesItsSession() throws Exception {
        AccountFixture account = createAccount("ACTIVE", true, "CUSTOMER");
        JsonNode first = login(account.email());
        JsonNode second = login(account.email());
        String firstToken = first.path("refresh_token").asText();
        String firstHash = hash(firstToken);
        String secondHash = hash(second.path("refresh_token").asText());
        TEST_CLOCK.set(expiresAt(firstHash).toInstant().plusSeconds(30));

        assertThat(post("/api/v1/auth/logout", Map.of("refresh_token", firstToken)).statusCode()).isEqualTo(204);
        OffsetDateTime initialRevocation = revokedAt(firstHash);
        assertThat(initialRevocation).isNotNull();
        TEST_CLOCK.set(TEST_CLOCK.instant().plusSeconds(10));
        assertThat(post("/api/v1/auth/logout", Map.of("refresh_token", firstToken)).statusCode()).isEqualTo(204);
        assertThat(revokedAt(firstHash)).isEqualTo(initialRevocation);
        assertThat(revokedAt(secondHash)).isNull();

        String pendingToken = login(createAccount("ACTIVE", true, "CUSTOMER").email())
                .path("refresh_token").asText();
        UUID pendingAccountId = accountId(hash(pendingToken));
        jdbcTemplate.update("UPDATE public.account SET account_status = 'PENDING_VERIFICATION' WHERE account_id = ?",
                pendingAccountId);
        assertThat(post("/api/v1/auth/logout", Map.of("refresh_token", pendingToken)).statusCode()).isEqualTo(204);
        assertThat(revokedAt(hash(pendingToken))).isNotNull();

        String unknown = randomRefreshToken();
        HttpResponse<String> unknownLogout = post("/api/v1/auth/logout", Map.of("refresh_token", unknown));
        assertThat(unknownLogout.statusCode()).isEqualTo(204);
        assertThat(unknownLogout.body()).isEmpty();
        assertThat(unknownLogout.headers().firstValue(HttpHeaders.CACHE_CONTROL)).contains("no-store");
    }

    @Test
    void loggingOutWithOldRotatedTokenDoesNotRevokeCurrentSession() throws Exception {
        AccountFixture account = createAccount("ACTIVE", true, "CUSTOMER");
        String old = login(account.email()).path("refresh_token").asText();
        String current = successfulRefresh(old).path("refresh_token").asText();
        assertThat(post("/api/v1/auth/logout", Map.of("refresh_token", old)).statusCode()).isEqualTo(204);
        assertThat(revokedAt(hash(old))).isNotNull();
        assertThat(revokedAt(hash(current))).isNull();
        successfulRefresh(current);
    }

    @Test
    void logoutBeforeRefreshMakesTheSameTokenUnusable() throws Exception {
        AccountFixture account = createAccount("ACTIVE", true, "CUSTOMER");
        String token = login(account.email()).path("refresh_token").asText();
        assertThat(post("/api/v1/auth/logout", Map.of("refresh_token", token)).statusCode()).isEqualTo(204);
        assertRefreshRejected(token);
        assertThat(countSessions(account.accountId())).isEqualTo(1);
    }

    @Test
    void logoutDatabaseFailureReturnsErrorAndRollsBackRevocation() throws Exception {
        AccountFixture account = createAccount("ACTIVE", true, "CUSTOMER");
        String token = login(account.email()).path("refresh_token").asText();
        String hash = hash(token);
        doThrow(new IllegalStateException("test-only revoke failure"))
                .when(refreshSessionRepository).flush();

        HttpResponse<String> response = post("/api/v1/auth/logout", Map.of("refresh_token", token));
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.headers().firstValue(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        assertThat(revokedAt(hash)).isNull();
    }

    @Test
    void twoConcurrentRefreshesOfSameTokenCreateExactlyOneNewSession() throws Exception {
        AccountFixture account = createAccount("ACTIVE", true, "CUSTOMER");
        String token = login(account.email()).path("refresh_token").asText();
        String tokenHash = hash(token);
        CyclicBarrier barrier = new CyclicBarrier(2);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (tokenHash.equals(invocation.getArgument(0))) {
                barrier.await(15, TimeUnit.SECONDS);
            }
            return result;
        }).when(refreshSessionRepository).findByTokenHash(tokenHash);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<HttpResponse<String>> first = executor.submit(() -> post("/api/v1/auth/refresh",
                    Map.of("refresh_token", token)));
            Future<HttpResponse<String>> second = executor.submit(() -> post("/api/v1/auth/refresh",
                    Map.of("refresh_token", token)));
            List<Integer> statuses = List.of(first.get(30, TimeUnit.SECONDS).statusCode(),
                    second.get(30, TimeUnit.SECONDS).statusCode());
            assertThat(statuses).containsExactlyInAnyOrder(200, 401);
        } finally {
            executor.shutdownNow();
        }
        assertThat(countSessions(account.accountId())).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.refresh_session "
                + "WHERE account_id = ? AND revoked_at IS NULL", Integer.class, account.accountId())).isEqualTo(1);
    }

    @Test
    void twoConcurrentLogoutsAreIdempotentAndDoNotTouchOtherSessions() throws Exception {
        AccountFixture account = createAccount("ACTIVE", true, "CUSTOMER");
        JsonNode first = login(account.email());
        JsonNode second = login(account.email());
        String token = first.path("refresh_token").asText();
        String tokenHash = hash(token);
        String otherHash = hash(second.path("refresh_token").asText());
        CyclicBarrier barrier = new CyclicBarrier(2);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (tokenHash.equals(invocation.getArgument(0))) {
                barrier.await(15, TimeUnit.SECONDS);
            }
            return result;
        }).when(refreshSessionRepository).findByTokenHash(tokenHash);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<HttpResponse<String>> firstLogout = executor.submit(() -> post("/api/v1/auth/logout",
                    Map.of("refresh_token", token)));
            Future<HttpResponse<String>> secondLogout = executor.submit(() -> post("/api/v1/auth/logout",
                    Map.of("refresh_token", token)));
            assertThat(List.of(firstLogout.get(30, TimeUnit.SECONDS).statusCode(),
                    secondLogout.get(30, TimeUnit.SECONDS).statusCode())).containsExactly(204, 204);
        } finally {
            executor.shutdownNow();
        }
        assertThat(revokedAt(tokenHash)).isNotNull();
        assertThat(revokedAt(otherHash)).isNull();
    }

    @Test
    void concurrentRefreshAndLogoutRespectWhicheverOperationLocksAccountFirst() throws Exception {
        AccountFixture account = createAccount("ACTIVE", true, "CUSTOMER");
        String oldToken = login(account.email()).path("refresh_token").asText();
        String oldHash = hash(oldToken);
        CyclicBarrier barrier = new CyclicBarrier(2);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (oldHash.equals(invocation.getArgument(0))) {
                barrier.await(15, TimeUnit.SECONDS);
            }
            return result;
        }).when(refreshSessionRepository).findByTokenHash(oldHash);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        HttpResponse<String> refresh;
        HttpResponse<String> logout;
        try {
            Future<HttpResponse<String>> refreshFuture = executor.submit(() -> post("/api/v1/auth/refresh",
                    Map.of("refresh_token", oldToken)));
            Future<HttpResponse<String>> logoutFuture = executor.submit(() -> post("/api/v1/auth/logout",
                    Map.of("refresh_token", oldToken)));
            refresh = refreshFuture.get(30, TimeUnit.SECONDS);
            logout = logoutFuture.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(refresh.statusCode()).isIn(200, 401);
        assertThat(revokedAt(oldHash)).isNotNull();
        if (refresh.statusCode() == 200) {
            JsonNode result = objectMapper.readTree(refresh.body());
            String newHash = hash(result.path("refresh_token").asText());
            assertThat(revokedAt(newHash)).isNull();
            assertThat(countSessions(account.accountId())).isEqualTo(2);
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.refresh_session "
                    + "WHERE account_id = ? AND revoked_at IS NULL", Integer.class, account.accountId())).isEqualTo(1);
        } else {
            assertThat(countSessions(account.accountId())).isEqualTo(1);
            assertThat(revokedAt(oldHash)).isNotNull();
        }
    }

    @Test
    void onlyTheExactPostRefreshAndLogoutRoutesArePublic() throws Exception {
        String token = randomRefreshToken();
        assertThat(post("/api/v1/auth/logout", Map.of("refresh_token", token)).statusCode()).isEqualTo(204);
        assertThat(post("/api/v1/auth/refresh-extra", Map.of("refresh_token", token)).statusCode())
                .isEqualTo(401);
        HttpRequest getRefresh = HttpRequest.newBuilder(uri("/api/v1/auth/refresh")).GET().build();
        assertThat(httpClient.send(getRefresh, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
    }

    private JsonNode login(String email) throws Exception {
        HttpResponse<String> response = post("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        return objectMapper.readTree(response.body());
    }

    private JsonNode successfulRefresh(String refreshToken) throws Exception {
        HttpResponse<String> response = post("/api/v1/auth/refresh", Map.of("refresh_token", refreshToken));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        JsonNode json = objectMapper.readTree(response.body());
        assertThat(json.size()).isEqualTo(4);
        assertThat(json.path("access_token").asText()).isNotBlank();
        assertThat(json.path("refresh_token").asText()).isNotBlank();
        assertThat(json.path("token_type").asText()).isEqualTo("Bearer");
        assertThat(json.path("expires_in").asLong()).isPositive();
        return json;
    }

    private HttpResponse<String> post(String path, Object body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(body)))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + serverPort + path);
    }

    private void assertInvalidRefresh(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        JsonNode problem = objectMapper.readTree(response.body());
        assertThat(problem.path("code").asText()).isEqualTo("INVALID_REFRESH_TOKEN");
        assertThat(problem.path("detail").asText()).isEqualTo("Refresh token is invalid");
    }

    private void assertSameInvalidRefreshProblem(
            HttpResponse<String> actual, HttpResponse<String> expected) throws Exception {
        JsonNode actualProblem = objectMapper.readTree(actual.body());
        JsonNode expectedProblem = objectMapper.readTree(expected.body());
        assertThat(actualProblem.path("code").asText()).isEqualTo(expectedProblem.path("code").asText());
        assertThat(actualProblem.path("detail").asText()).isEqualTo(expectedProblem.path("detail").asText());
    }

    private void assertRefreshRejected(String token) throws Exception {
        assertInvalidRefresh(post("/api/v1/auth/refresh", Map.of("refresh_token", token)));
    }

    private void assertBadRequest(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(400);
        JsonNode problem = objectMapper.readTree(response.body());
        assertThat(problem.path("code").asText()).isEqualTo("INVALID_REQUEST");
    }

    private void assertAccountNotActive(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(403);
        JsonNode problem = objectMapper.readTree(response.body());
        assertThat(problem.path("code").asText()).isEqualTo("ACCOUNT_NOT_ACTIVE");
    }

    private AccountFixture createAccount(String status, boolean verified, String... roles) {
        UUID accountId = UUID.randomUUID();
        String email = "refresh-" + UUID.randomUUID() + EMAIL_DOMAIN;
        OffsetDateTime now = OffsetDateTime.ofInstant(TEST_CLOCK.instant(), ZoneOffset.UTC);
        jdbcTemplate.update("""
                INSERT INTO public.account (
                    account_id, email, password_hash, account_status, email_verified_at, registered_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, accountId, email, passwordEncoder.encode(PASSWORD), status, verified ? now : null, now);
        for (String role : roles) {
            jdbcTemplate.update("INSERT INTO public.account_role (account_id, role_code) VALUES (?, ?)",
                    accountId, role);
        }
        return new AccountFixture(accountId, email);
    }

    private String hash(String token) {
        return jwtTokenService.refreshTokenHash(token);
    }

    private OffsetDateTime issuedAt(String tokenHash) {
        return jdbcTemplate.queryForObject("SELECT issued_at FROM public.refresh_session WHERE token_hash = ?",
                OffsetDateTime.class, tokenHash);
    }

    private OffsetDateTime expiresAt(String tokenHash) {
        return jdbcTemplate.queryForObject("SELECT expires_at FROM public.refresh_session WHERE token_hash = ?",
                OffsetDateTime.class, tokenHash);
    }

    private OffsetDateTime revokedAt(String tokenHash) {
        return jdbcTemplate.queryForObject("SELECT revoked_at FROM public.refresh_session WHERE token_hash = ?",
                OffsetDateTime.class, tokenHash);
    }

    private UUID accountId(String tokenHash) {
        return jdbcTemplate.queryForObject("SELECT account_id FROM public.refresh_session WHERE token_hash = ?",
                UUID.class, tokenHash);
    }

    private int countSessions(UUID accountId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM public.refresh_session WHERE account_id = ?",
                Integer.class, accountId);
    }

    private static String randomRefreshToken() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record AccountFixture(UUID accountId, String email) {
    }

    private static final class DurationSeconds {
        static long between(Instant start, Instant end) {
            return java.time.Duration.between(start, end).toSeconds();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfiguration {
        @Bean
        @Primary
        Clock refreshTestClock() {
            return TEST_CLOCK;
        }
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;

        private MutableClock(Instant initial) {
            current = new AtomicReference<>(initial);
        }

        void set(Instant instant) {
            current.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }
}
