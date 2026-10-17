package com.stylemind.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.identity.repository.account.AccountRepository;
import com.stylemind.identity.repository.passwordreset.PasswordResetRepository;
import com.stylemind.identity.repository.session.RefreshSessionRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@SpringBootTest(properties = {"management.health.db.enabled=false"})
@AutoConfigureMockMvc
@Testcontainers
@Import(PasswordResetPostgresIntegrationTests.TestClockConfiguration.class)
class PasswordResetPostgresIntegrationTests {

    private static final String HMAC_SECRET = randomSecret();
    private static final String SERVICE_TOKEN = randomSecret();
    private static final Network NETWORK = Network.newNetwork();
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6")
            .withDatabaseName("stylemind_identity")
            .withUsername("identity_test")
            .withPassword("identity_test_only");
    private static final GenericContainer<?> MAILPIT = new GenericContainer<>("axllent/mailpit:v1.31.4")
            .withNetwork(NETWORK)
            .withNetworkAliases("mailpit")
            .withExposedPorts(1025, 8025)
            .withEnv("MP_ENABLE_CHAOS", "true")
            .waitingFor(Wait.forListeningPort());
    private static final GenericContainer<?> NOTIFICATION = notificationContainer();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES_CONTAINER = POSTGRES;
    @Container
    private static final GenericContainer<?> MAILPIT_CONTAINER = MAILPIT;
    @Container
    private static final GenericContainer<?> NOTIFICATION_CONTAINER = NOTIFICATION;

    private static final MutableClock TEST_CLOCK = new MutableClock(Instant.now().minusSeconds(2));
    private static final Pattern SIX_DIGITS = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockitoSpyBean private AccountRepository accountRepository;
    @MockitoSpyBean private PasswordResetRepository passwordResetRepository;
    @MockitoSpyBean private RefreshSessionRepository refreshSessionRepository;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("stylemind.verification.hmac-secret", () -> HMAC_SECRET);
        registry.add("stylemind.notification.service-token", () -> SERVICE_TOKEN);
        JwtTestSupport.registerProperties(registry);
        registry.add("stylemind.notification.base-url", () -> "http://" + NOTIFICATION.getHost() + ":"
                + NOTIFICATION.getMappedPort(8086));
    }

    @BeforeEach
    void clearDisposableDatabaseAndSetCurrentClock() throws Exception {
        jdbcTemplate.update("DELETE FROM public.password_reset_challenge");
        jdbcTemplate.update("DELETE FROM public.email_verification_challenge");
        jdbcTemplate.update("DELETE FROM public.refresh_session");
        jdbcTemplate.update("DELETE FROM public.account_role");
        jdbcTemplate.update("DELETE FROM public.account");
        TEST_CLOCK.set(Instant.now().minusSeconds(2));
        setMailpitChaos("{}");
        clearMailpitMessages();
    }

    @Test
    void eligibleAccountReceivesRealMailpitOtpAndPublicResponseDoesNotRevealEligibility() throws Exception {
        String email = email();
        UUID accountId = createAccount(email, "old-password-value-2026", "ACTIVE", true);

        MvcResult eligible = requestOtp(email);
        String message = latestEmailText();
        JsonNode headers = latestEmailHeaders();
        String code = extractCode(message);

        assertThat(eligible.getResponse().getStatus()).isEqualTo(202);
        assertThat(eligible.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(objectMapper.readTree(eligible.getResponse().getContentAsByteArray()).path("message").asText())
                .isEqualTo("If the account is eligible, password reset instructions will be sent.");
        assertThat(message).contains("password reset code", "5 minutes");
        assertThat(headers.path("To").toString()).contains(email);
        assertThat(jdbcTemplate.queryForObject("SELECT account_status FROM public.account WHERE account_id = ?",
                String.class, accountId)).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject("SELECT password_hash FROM public.account WHERE account_id = ?",
                String.class, accountId)).isNotEqualTo("old-password-value-2026");

        MvcResult unknown = requestOtp(email());
        assertThat(unknown.getResponse().getStatus()).isEqualTo(202);
        assertThat(unknown.getResponse().getContentAsString()).isEqualTo(eligible.getResponse().getContentAsString());
        assertThat(extractCode(latestEmailText())).isEqualTo(code);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.password_reset_challenge",
                Integer.class)).isEqualTo(1);

        MvcResult malformed = requestOtp("not-an-email");
        assertProblem(malformed, 400, "INVALID_REQUEST");
    }

    @Test
    void wrongOtpAttemptsCommitAndTheFifthAttemptLocksOnlyTheResetChallenge() throws Exception {
        String email = email();
        UUID accountId = createAccount(email, "old-password-value-2026", "ACTIVE", true);
        requestOtp(email);
        String correctCode = extractCode(latestEmailText());
        String wrongCode = correctCode.equals("000000") ? "000001" : "000000";

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertProblem(verify(email, wrongCode), 400, "PASSWORD_RESET_VERIFICATION_INVALID");
            assertThat(jdbcTemplate.queryForObject("SELECT failed_attempts FROM public.password_reset_challenge",
                    Integer.class)).isEqualTo(attempt);
        }
        MvcResult locked = verify(email, correctCode);
        assertProblem(locked, 429, "PASSWORD_RESET_ATTEMPTS_EXCEEDED");
        assertThat(locked.getResponse().getHeader("Retry-After")).isEqualTo("900");
        UUID oldChallengeId = jdbcTemplate.queryForObject(
                "SELECT challenge_id FROM public.password_reset_challenge", UUID.class);
        byte[] oldDigest = jdbcTemplate.queryForObject(
                "SELECT otp_digest FROM public.password_reset_challenge", byte[].class);
        assertProblem(requestOtp(email), 202, null);
        assertThat(jdbcTemplate.queryForObject("SELECT send_count FROM public.password_reset_challenge",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT account_status FROM public.account WHERE account_id = ?",
                String.class, accountId)).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.refresh_session WHERE account_id = ?",
                Integer.class, accountId)).isZero();

        TEST_CLOCK.advance(Duration.ofMinutes(15));
        assertProblem(verify(email, correctCode), 400, "PASSWORD_RESET_VERIFICATION_INVALID");
        assertThat(requestOtp(email).getResponse().getStatus()).isEqualTo(202);
        assertThat(jdbcTemplate.queryForObject("SELECT challenge_id FROM public.password_reset_challenge",
                UUID.class)).isNotEqualTo(oldChallengeId);
        assertThat(jdbcTemplate.queryForObject("SELECT otp_digest FROM public.password_reset_challenge",
                byte[].class)).isNotEqualTo(oldDigest);
        assertThat(jdbcTemplate.queryForObject("SELECT failed_attempts FROM public.password_reset_challenge",
                Integer.class)).isZero();
        String newCode = extractCode(latestEmailText());
        assertThat(verify(email, newCode).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void otpExpiresAtExactFiveMinuteBoundaryAndMalformedCodeDoesNotCount() throws Exception {
        String email = email();
        createAccount(email, "old-password-value-2026", "ACTIVE", true);
        requestOtp(email);
        String code = extractCode(latestEmailText());

        assertProblem(verify(email, "123"), 400, "INVALID_REQUEST");
        assertThat(jdbcTemplate.queryForObject("SELECT failed_attempts FROM public.password_reset_challenge",
                Integer.class)).isZero();
        TEST_CLOCK.advance(Duration.ofMinutes(5));
        assertProblem(verify(email, code), 400, "PASSWORD_RESET_VERIFICATION_INVALID");
        assertThat(jdbcTemplate.queryForObject("SELECT verified_at FROM public.password_reset_challenge",
                OffsetDateTime.class)).isNull();
    }

    @Test
    void otpIsOneUseResetTokenStoresOnlySha256AndAnActiveGrantCannotBeCancelledByNewEmailRequest()
            throws Exception {
        String email = email();
        UUID accountId = createAccount(email, "old-password-value-2026", "ACTIVE", true);
        createSession(accountId, "prior-session-hash");
        requestOtp(email);
        String code = extractCode(latestEmailText());

        JsonNode issued = json(verify(email, code));
        String resetToken = issued.path("reset_token").asText();
        assertThat(verify(email, code).getResponse().getStatus()).isEqualTo(400);
        assertThat(issued.path("expires_in").asLong()).isEqualTo(300);
        assertThat(resetToken).hasSize(43);
        byte[] storedHash = jdbcTemplate.queryForObject(
                "SELECT reset_token_hash FROM public.password_reset_challenge", byte[].class);
        assertThat(storedHash).containsExactly(MessageDigest.getInstance("SHA-256")
                .digest(resetToken.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        assertThat(new String(storedHash, java.nio.charset.StandardCharsets.US_ASCII)).doesNotContain(resetToken);

        UUID challengeId = jdbcTemplate.queryForObject(
                "SELECT challenge_id FROM public.password_reset_challenge", UUID.class);
        MvcResult repeatedRequest = requestOtp(email);
        assertThat(repeatedRequest.getResponse().getStatus()).isEqualTo(202);
        assertThat(jdbcTemplate.queryForObject("SELECT challenge_id FROM public.password_reset_challenge",
                UUID.class)).isEqualTo(challengeId);
        assertThat(jdbcTemplate.queryForObject("SELECT send_count FROM public.password_reset_challenge",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT revoked_at FROM public.refresh_session",
                OffsetDateTime.class)).isNull();

        assertThat(reset(resetToken, "new-password-value-2026").getResponse().getStatus()).isEqualTo(204);
        assertProblem(reset(resetToken, "another-new-password-2026"), 400, "PASSWORD_RESET_INVALID");
        String resultingHash = jdbcTemplate.queryForObject("SELECT password_hash FROM public.account WHERE account_id = ?",
                String.class, accountId);
        assertThat(passwordEncoder.matches("new-password-value-2026", resultingHash)).isTrue();
        assertThat(passwordEncoder.matches("another-new-password-2026", resultingHash)).isFalse();
    }

    @Test
    void resetUpdatesPasswordConsumesGrantAndRevokesEveryActiveRefreshSessionButKeepsAccessJwtValid()
            throws Exception {
        String email = email();
        UUID accountId = createAccount(email, "old-password-value-2026", "ACTIVE", true);
        createSession(accountId, "active-session-one");
        createSession(accountId, "active-session-two");
        OffsetDateTime alreadyRevoked = OffsetDateTime.now(TEST_CLOCK);
        alreadyRevoked = alreadyRevoked.withNano((alreadyRevoked.getNano() / 1_000) * 1_000).minusHours(1);
        createSession(accountId, "already-revoked-session", alreadyRevoked);
        JsonNode login = json(postJson("/api/v1/auth/login", Map.of(
                "email", email, "password", "old-password-value-2026")));
        assertThat(login.path("access_token").asText()).isNotBlank();
        assertThat(login.path("refresh_token").asText()).isNotBlank();

        requestOtp(email);
        String code = extractCode(latestEmailText());
        String resetToken = json(verify(email, code)).path("reset_token").asText();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.refresh_session WHERE account_id = ? "
                + "AND revoked_at IS NULL", Integer.class, accountId)).isEqualTo(3);

        assertThat(reset(resetToken, "  new password is exact  ").getResponse().getStatus()).isEqualTo(204);
        String storedPasswordHash = jdbcTemplate.queryForObject("SELECT password_hash FROM public.account "
                + "WHERE account_id = ?", String.class, accountId);
        assertThat(passwordEncoder.matches("  new password is exact  ", storedPasswordHash)).isTrue();
        assertThat(passwordEncoder.matches("new password is exact", storedPasswordHash)).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT consumed_at IS NOT NULL FROM "
                + "public.password_reset_challenge WHERE account_id = ?", Boolean.class, accountId)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.refresh_session WHERE account_id = ? "
                + "AND revoked_at IS NULL", Integer.class, accountId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT revoked_at FROM public.refresh_session "
                + "WHERE token_hash = 'already-revoked-session'", OffsetDateTime.class)).isEqualTo(alreadyRevoked);

        assertThat(postJson("/api/v1/auth/login", Map.of("email", email,
                "password", "old-password-value-2026")).getResponse().getStatus()).isEqualTo(401);
        assertProblem(reset(login.path("access_token").asText(), "new-password-value-2026"),
                400, "INVALID_REQUEST");
        MvcResult loginWithNewPassword = postJson("/api/v1/auth/login", Map.of("email", email,
                "password", "  new password is exact  "));
        assertThat(loginWithNewPassword.getResponse().getStatus()).isEqualTo(200);
        String accessToken = login.path("access_token").asText();
        MvcResult protectedUnmapped = mockMvc.perform(get("/api/v1/auth/not-an-endpoint")
                .header("Authorization", "Bearer " + accessToken)).andReturn();
        assertThat(protectedUnmapped.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void resetGrantExpiresAtExactBoundaryAndInactiveAccountCannotBeResetOrActivated() throws Exception {
        String email = email();
        UUID accountId = createAccount(email, "old-password-value-2026", "ACTIVE", true);
        requestOtp(email);
        String code = extractCode(latestEmailText());
        String resetToken = json(verify(email, code)).path("reset_token").asText();

        TEST_CLOCK.advance(Duration.ofMinutes(5));
        assertProblem(reset(resetToken, "new-password-value-2026"), 400, "PASSWORD_RESET_INVALID");
        assertThat(passwordEncoder.matches("old-password-value-2026", jdbcTemplate.queryForObject(
                "SELECT password_hash FROM public.account WHERE account_id = ?", String.class, accountId))).isTrue();

        String secondEmail = email();
        UUID pendingId = createAccount(secondEmail, "old-password-value-2026", "PENDING_VERIFICATION", false);
        MvcResult pending = requestOtp(secondEmail);
        assertThat(pending.getResponse().getStatus()).isEqualTo(202);
        assertThat(pending.getResponse().getContentAsString()).contains("If the account is eligible");
        assertProblem(verify(secondEmail, "000000"), 400, "PASSWORD_RESET_VERIFICATION_INVALID");
        assertThat(jdbcTemplate.queryForObject("SELECT account_status FROM public.account WHERE account_id = ?",
                String.class, pendingId)).isEqualTo("PENDING_VERIFICATION");

        String lockedEmail = email();
        UUID lockedId = createAccount(lockedEmail, "old-password-value-2026", "ACTIVE", true);
        requestOtp(lockedEmail);
        String lockedCode = extractCode(latestEmailText());
        String lockedGrant = json(verify(lockedEmail, lockedCode)).path("reset_token").asText();
        jdbcTemplate.update("UPDATE public.account SET account_status = 'LOCKED' WHERE account_id = ?", lockedId);
        assertProblem(reset(lockedGrant, "new-password-value-2026"), 400, "PASSWORD_RESET_INVALID");
        assertThat(jdbcTemplate.queryForObject("SELECT account_status FROM public.account WHERE account_id = ?",
                String.class, lockedId)).isEqualTo("LOCKED");
    }

    @Test
    void registrationOtpCannotBeUsedAsPasswordResetOtp() throws Exception {
        String email = email();
        assertThat(postJson("/api/v1/auth/register", Map.of("email", email,
                "password", "old-password-value-2026")).getResponse().getStatus()).isEqualTo(201);
        String registrationCode = extractCode(latestEmailText());
        assertThat(postJson("/api/v1/auth/verify-email", Map.of("email", email, "code", registrationCode))
                .getResponse().getStatus()).isEqualTo(204);

        assertProblem(verify(email, registrationCode), 400, "PASSWORD_RESET_VERIFICATION_INVALID");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.password_reset_challenge",
                Integer.class)).isZero();
    }

    @Test
    void passwordResetOtpSendCooldownAndDeliveryFailureRemainGenericAndCountTheAttempt() throws Exception {
        String email = email();
        UUID accountId = createAccount(email, "old-password-value-2026", "ACTIVE", true);
        setMailpitChaos("{\"Recipient\":{\"ErrorCode\":451,\"Probability\":100}}");

        MvcResult failure = requestOtp(email);

        assertThat(failure.getResponse().getStatus()).isEqualTo(202);
        assertThat(failure.getResponse().getContentAsString()).contains("If the account is eligible");
        assertThat(jdbcTemplate.queryForObject("SELECT send_count FROM public.password_reset_challenge",
                Integer.class)).isEqualTo(1);
        assertThat(requestOtp(email).getResponse().getContentAsString())
                .isEqualTo(failure.getResponse().getContentAsString());
        assertThat(jdbcTemplate.queryForObject("SELECT send_count FROM public.password_reset_challenge",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT account_status FROM public.account WHERE account_id = ?",
                String.class, accountId)).isEqualTo("ACTIVE");
        setMailpitChaos("{}");
    }

    @Test
    void requestOtpEnforcesThreeSendsPerHourAndStartsANewWindowAtTheBoundary() throws Exception {
        String email = email();
        createAccount(email, "old-password-value-2026", "ACTIVE", true);
        TEST_CLOCK.set(TEST_CLOCK.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        Instant firstSend = TEST_CLOCK.instant();
        assertThat(requestOtp(email).getResponse().getStatus()).isEqualTo(202);
        TEST_CLOCK.advance(Duration.ofSeconds(60));
        assertThat(requestOtp(email).getResponse().getStatus()).isEqualTo(202);
        TEST_CLOCK.advance(Duration.ofSeconds(60));
        assertThat(requestOtp(email).getResponse().getStatus()).isEqualTo(202);
        TEST_CLOCK.advance(Duration.ofSeconds(60));

        assertThat(requestOtp(email).getResponse().getStatus()).isEqualTo(202);
        assertThat(jdbcTemplate.queryForObject("SELECT send_count FROM public.password_reset_challenge",
                Integer.class)).isEqualTo(3);
        TEST_CLOCK.set(firstSend.plus(Duration.ofHours(1)));
        assertThat(requestOtp(email).getResponse().getStatus()).isEqualTo(202);
        assertThat(jdbcTemplate.queryForObject("SELECT send_count FROM public.password_reset_challenge",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void accountPasswordGrantAndSessionChangesRollbackTogetherWhenSessionRevocationFails() throws Exception {
        String email = email();
        UUID accountId = createAccount(email, "old-password-value-2026", "ACTIVE", true);
        createSession(accountId, "rollback-session");
        requestOtp(email);
        String resetToken = json(verify(email, extractCode(latestEmailText()))).path("reset_token").asText();
        String originalHash = jdbcTemplate.queryForObject("SELECT password_hash FROM public.account WHERE account_id = ?",
                String.class, accountId);
        doThrow(new IllegalStateException("test-only session revocation failure"))
                .when(refreshSessionRepository).saveAll(any(Iterable.class));

        assertThat(reset(resetToken, "new-password-value-2026").getResponse().getStatus()).isEqualTo(500);
        assertThat(jdbcTemplate.queryForObject("SELECT password_hash FROM public.account WHERE account_id = ?",
                String.class, accountId)).isEqualTo(originalHash);
        assertThat(jdbcTemplate.queryForObject("SELECT consumed_at FROM public.password_reset_challenge "
                + "WHERE account_id = ?", OffsetDateTime.class, accountId)).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT revoked_at FROM public.refresh_session "
                + "WHERE token_hash = 'rollback-session'", OffsetDateTime.class)).isNull();
    }

    @Test
    void concurrentOtpVerificationAndPasswordResetEachAllowOnlyOneUse() throws Exception {
        String email = email();
        createAccount(email, "old-password-value-2026", "ACTIVE", true);
        requestOtp(email);
        String code = extractCode(latestEmailText());
        String verifyBody = objectMapper.writeValueAsString(Map.of("email", email, "code", code));
        List<MvcResult> verifyResults = concurrentPostResults("/api/v1/auth/verify-password-reset", verifyBody);
        assertThat(verifyResults.stream().map(result -> result.getResponse().getStatus()).toList())
                .containsExactlyInAnyOrder(200, 400);
        String nextToken = objectMapper.readTree(verifyResults.stream()
                .filter(result -> result.getResponse().getStatus() == 200).findFirst().orElseThrow()
                .getResponse().getContentAsByteArray()).path("reset_token").asText();
        String resetBody = objectMapper.writeValueAsString(Map.of(
                "reset_token", nextToken, "new_password", "next-password-value-2026"));
        List<MvcResult> resetResults = concurrentPostResults("/api/v1/auth/reset-password", resetBody);
        assertThat(resetResults.stream().map(result -> result.getResponse().getStatus()).toList())
                .containsExactlyInAnyOrder(204, 400);
    }

    @Test
    void concurrentResetAndRefreshLeaveNoActiveSessionAfterResetCommits() throws Exception {
        String email = email();
        createAccount(email, "old-password-value-2026", "ACTIVE", true);
        JsonNode login = json(postJson("/api/v1/auth/login", Map.of(
                "email", email, "password", "old-password-value-2026")));
        requestOtp(email);
        String resetToken = json(verify(email, extractCode(latestEmailText()))).path("reset_token").asText();
        String resetBody = objectMapper.writeValueAsString(Map.of(
                "reset_token", resetToken, "new_password", "new-password-value-2026"));
        String refreshBody = objectMapper.writeValueAsString(Map.of("refresh_token", login.path("refresh_token").asText()));
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> resetResult = pool.submit(() -> postAfterBarrier(
                    "/api/v1/auth/reset-password", resetBody, barrier));
            Future<Integer> refreshResult = pool.submit(() -> postAfterBarrier(
                    "/api/v1/auth/refresh", refreshBody, barrier));
            assertThat(resetResult.get(30, TimeUnit.SECONDS)).isEqualTo(204);
            assertThat(refreshResult.get(30, TimeUnit.SECONDS)).isIn(200, 401);
        } finally {
            pool.shutdownNow();
        }
        UUID accountId = jdbcTemplate.queryForObject("SELECT account_id FROM public.account WHERE email = ?",
                UUID.class, email);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.refresh_session WHERE account_id = ? "
                + "AND revoked_at IS NULL", Integer.class, accountId)).isZero();
    }

    private UUID createAccount(String email, String password, String status, boolean verified) {
        UUID accountId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(TEST_CLOCK).withOffsetSameInstant(ZoneOffset.UTC);
        jdbcTemplate.update("""
                INSERT INTO public.account(account_id, email, password_hash, account_status, email_verified_at, registered_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, accountId, email, passwordEncoder.encode(password), status, verified ? now : null, now);
        jdbcTemplate.update("INSERT INTO public.account_role(account_id, role_code) VALUES (?, 'CUSTOMER')", accountId);
        return accountId;
    }

    private void createSession(UUID accountId, String tokenHash) {
        createSession(accountId, tokenHash, null);
    }

    private void createSession(UUID accountId, String tokenHash, OffsetDateTime revokedAt) {
        OffsetDateTime now = OffsetDateTime.now(TEST_CLOCK).withOffsetSameInstant(ZoneOffset.UTC);
        jdbcTemplate.update("""
                INSERT INTO public.refresh_session(session_id, account_id, token_hash, issued_at, expires_at, revoked_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), accountId, tokenHash, now, now.plusDays(7), revokedAt);
    }

    private MvcResult requestOtp(String email) throws Exception {
        return postJson("/api/v1/auth/forgot-password", Map.of("email", email));
    }

    private MvcResult verify(String email, String code) throws Exception {
        return postJson("/api/v1/auth/verify-password-reset", Map.of("email", email, "code", code));
    }

    private MvcResult reset(String token, String password) throws Exception {
        return postJson("/api/v1/auth/reset-password", Map.of("reset_token", token, "new_password", password));
    }

    private MvcResult postJson(String path, Object body) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andReturn();
    }

    private Integer postAfterBarrier(String path, String body, CyclicBarrier barrier) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus();
    }

    private List<MvcResult> concurrentPostResults(String path, String body) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> first = pool.submit(() -> postAfterBarrierResult(path, body, barrier));
            Future<MvcResult> second = pool.submit(() -> postAfterBarrierResult(path, body, barrier));
            return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    private MvcResult postAfterBarrierResult(String path, String body, CyclicBarrier barrier) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private String latestEmailText() throws Exception {
        String host = MAILPIT.getHost();
        int port = MAILPIT.getMappedPort(8025);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + host + ":" + port + "/view/latest.txt"))
                .timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    private JsonNode latestEmailHeaders() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + MAILPIT.getHost() + ":"
                        + MAILPIT.getMappedPort(8025) + "/api/v1/message/latest/headers"))
                .timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }

    private void clearMailpitMessages() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + MAILPIT.getHost() + ":"
                        + MAILPIT.getMappedPort(8025) + "/api/v1/messages"))
                .timeout(Duration.ofSeconds(5)).DELETE().build();
        HttpResponse<Void> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isIn(200, 204);
    }

    private String extractCode(String message) {
        Matcher matcher = SIX_DIGITS.matcher(message);
        assertThat(matcher.find()).as("Mailpit must contain an OTP accepted by SMTP").isTrue();
        return matcher.group(1);
    }

    private void setMailpitChaos(String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + MAILPIT.getHost() + ":"
                        + MAILPIT.getMappedPort(8025) + "/api/v1/chaos"))
                .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json)).build();
        HttpResponse<Void> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isEqualTo(200);
    }

    private JsonNode json(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private void assertProblem(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        if (code == null) {
            return;
        }
        JsonNode problem = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(problem.path("code").asText()).isEqualTo(code);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("password_hash", "otp_digest");
    }

    private String email() {
        return "reset-" + UUID.randomUUID() + "@example.test";
    }

    private static GenericContainer<?> notificationContainer() {
        Path jar = locateRepositoryRoot().resolve("notification-service/target/notification-service-0.0.1-SNAPSHOT.jar");
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException("Build notification-service before Identity E2E tests: " + jar);
        }
        return new GenericContainer<>("eclipse-temurin:21-jre-noble")
                .withCopyFileToContainer(MountableFile.forHostPath(jar), "/app/app.jar")
                .withCommand("java", "-jar", "/app/app.jar")
                .withNetwork(NETWORK)
                .withExposedPorts(8086)
                .withEnv("SERVER_PORT", "8086")
                .withEnv("STYLEMIND_SERVICE_TOKEN", SERVICE_TOKEN)
                .withEnv("STYLEMIND_SMTP_HOST", "mailpit")
                .withEnv("STYLEMIND_SMTP_PORT", "1025")
                .withEnv("STYLEMIND_SMTP_AUTH", "false")
                .withEnv("STYLEMIND_EMAIL_FROM", "integration-test@stylemind.local")
                .withEnv("SPRING_AUTOCONFIGURE_EXCLUDE",
                        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                                + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                                + "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration")
                .withEnv("MANAGEMENT_HEALTH_DB_ENABLED", "false")
                .waitingFor(Wait.forHttp("/actuator/health").forPort(8086).forStatusCode(200)
                        .withStartupTimeout(Duration.ofSeconds(90)));
    }

    private static Path locateRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        if (Files.isDirectory(current.resolve("notification-service"))) {
            return current;
        }
        if (Files.isDirectory(current.getParent().resolve("notification-service"))) {
            return current.getParent();
        }
        throw new IllegalStateException("Could not locate repository root for Notification integration test");
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfiguration {
        @Bean @Primary
        Clock integrationClock() { return TEST_CLOCK; }
    }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;
        MutableClock(Instant instant) { this.instant = new AtomicReference<>(instant); }
        void set(Instant value) { instant.set(value); }
        void advance(Duration duration) { instant.updateAndGet(value -> value.plus(duration)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant.get(); }
    }
}
