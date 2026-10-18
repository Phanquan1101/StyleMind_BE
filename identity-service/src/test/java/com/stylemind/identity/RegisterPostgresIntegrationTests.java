package com.stylemind.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.identity.logging.CorrelationIdFilter;
import com.stylemind.identity.model.emailverification.EmailVerificationChallenge;
import com.stylemind.identity.repository.account.AccountRepository;
import com.stylemind.identity.repository.emailverification.EmailVerificationRepository;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
import org.slf4j.LoggerFactory;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
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
@Import(RegisterPostgresIntegrationTests.TestClockConfiguration.class)
class RegisterPostgresIntegrationTests {

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

    private static final MutableClock TEST_CLOCK = new MutableClock(Instant.parse("2026-10-06T00:00:00Z"));
    private static final Pattern SIX_DIGITS = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)");
    private String emailForCurrentTest;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @MockitoSpyBean
    private EmailVerificationRepository challengeRepository;

    @MockitoSpyBean
    private AccountRepository accountRepository;

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
    void cleanDisposableDatabaseAndResetClock() throws Exception {
        jdbcTemplate.update("DELETE FROM public.email_verification_challenge");
        jdbcTemplate.update("DELETE FROM public.refresh_session");
        jdbcTemplate.update("DELETE FROM public.account_role");
        jdbcTemplate.update("DELETE FROM public.account");
        TEST_CLOCK.set(Instant.parse("2026-10-06T00:00:00Z"));
        emailForCurrentTest = null;
        setMailpitChaos("{}");
    }

    @Test
    void registerSendsRealSmtpEmailAndValidCodeActivatesAccountExactlyOnce() throws Exception {
        String email = email();
        String rawPassword = "  password is kept exactly  ";
        Logger requestLogger = (Logger) LoggerFactory.getLogger(CorrelationIdFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        requestLogger.addAppender(appender);
        MvcResult registered;
        String code;
        try {
            registered = register(email, rawPassword);
            code = latestEmailCode();
        } finally {
            requestLogger.detachAppender(appender);
            appender.stop();
        }

        assertThat(registered.getResponse().getStatus()).isEqualTo(201);
        JsonNode response = objectMapper.readTree(registered.getResponse().getContentAsByteArray());
        assertThat(response.path("email").asText()).isEqualTo(email);
        assertThat(response.path("account_status").asText()).isEqualTo("PENDING_VERIFICATION");
        assertThat(response.size()).isEqualTo(3);
        assertThat(registered.getResponse().getContentAsString()).doesNotContain("password_hash", "verificationCode");
        UUID accountId = UUID.fromString(response.path("account_id").asText());
        Map<String, Object> account = jdbcTemplate.queryForMap("""
                SELECT email, password_hash, account_status, email_verified_at, registered_at
                FROM public.account WHERE account_id = ?
                """, accountId);
        String passwordHash = (String) account.get("password_hash");
        assertThat(account.get("email")).isEqualTo(email);
        assertThat(account.get("account_status")).isEqualTo("PENDING_VERIFICATION");
        assertThat(account.get("email_verified_at")).isNull();
        assertThat(account.get("registered_at")).isNotNull();
        assertThat(passwordHash).isNotEqualTo(rawPassword);
        assertThat(passwordEncoder.matches(rawPassword, passwordHash)).isTrue();
        assertThat(passwordEncoder.matches(rawPassword.trim(), passwordHash)).isFalse();
        assertThat(jdbcTemplate.queryForList(
                "SELECT role_code FROM public.account_role WHERE account_id = ?", String.class, accountId))
                .containsExactly("CUSTOMER");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.email_verification_challenge",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.user_profile WHERE account_id = ?",
                Integer.class, accountId)).as("Register must not create a profile placeholder").isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.style_profile WHERE account_id = ?",
                Integer.class, accountId)).as("Register must not create a style-profile placeholder").isZero();
        String safeLogs = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
        assertThat(safeLogs).doesNotContain(code, rawPassword, SERVICE_TOKEN, HMAC_SECRET);

        assertThat(postJson("/api/v1/auth/verify-email", Map.of("email", email, "code", code))
                .getResponse().getStatus()).isEqualTo(204);
        assertThat(jdbcTemplate.queryForObject("SELECT account_status FROM public.account WHERE email = ?",
                String.class, email)).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject("SELECT email_verified_at IS NOT NULL FROM public.account WHERE email = ?",
                Boolean.class, email)).isTrue();
        assertProblem(postJson("/api/v1/auth/verify-email", Map.of("email", email, "code", code)), 409,
                "EMAIL_ALREADY_VERIFIED");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.email_verification_challenge",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void registeredAndVerifiedAccountCanLoginWithRealAccessAndRefreshTokens() throws Exception {
        String email = email();
        String normalizedVariant = "  " + email.toUpperCase(java.util.Locale.ROOT) + "  ";
        String rawPassword = "  password is kept exactly  ";

        MvcResult registration = register(normalizedVariant, rawPassword);
        assertThat(registration.getResponse().getStatus()).isEqualTo(201);
        JsonNode registrationBody = objectMapper.readTree(registration.getResponse().getContentAsByteArray());
        String code = latestEmailCode();
        assertThat(postJson("/api/v1/auth/verify-email", Map.of("email", normalizedVariant, "code", code))
                .getResponse().getStatus()).isEqualTo(204);

        TEST_CLOCK.set(Instant.now().minusSeconds(1));
        MvcResult login = postJson("/api/v1/auth/login",
                Map.of("email", normalizedVariant, "password", rawPassword));
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        assertThat(login.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        JsonNode body = objectMapper.readTree(login.getResponse().getContentAsByteArray());
        assertThat(body.path("token_type").asText()).isEqualTo("Bearer");
        assertThat(body.path("expires_in").asLong()).isEqualTo(900);
        Jwt accessToken = jwtDecoder.decode(body.path("access_token").asText());
        UUID accountId = UUID.fromString(registrationBody.path("account_id").asText());
        assertThat(accessToken.getSubject()).isEqualTo(accountId.toString());
        assertThat(accessToken.getClaimAsStringList("roles")).containsExactly("CUSTOMER");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.refresh_session WHERE account_id = ?",
                Integer.class, accountId)).isEqualTo(1);
    }

    @Test
    void duplicateNormalizedEmailDoesNotCreateAnotherChallengeOrSendAnotherMessage() throws Exception {
        String email = email();
        assertThat(register(email).getResponse().getStatus()).isEqualTo(201);
        String initialCode = latestEmailCode();

        MvcResult duplicate = postJson("/api/v1/auth/register", Map.of(
                "email", "  " + email.toUpperCase(java.util.Locale.ROOT) + "  ",
                "password", "test-password-value-2026"));

        assertProblem(duplicate, 409, "EMAIL_ALREADY_EXISTS");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.account WHERE email = ?",
                Integer.class, email)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.email_verification_challenge",
                Integer.class)).isEqualTo(1);
        assertThat(latestEmailCode()).isEqualTo(initialCode);
    }

    @Test
    void fifthWrongCodeLocksVerifyAndResendUntilExpiryThenRequiresRotation() throws Exception {
        String email = email();
        register(email);
        String correctCode = latestEmailCode();
        String wrongCode = correctCode.equals("000000") ? "000001" : "000000";

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertProblem(postJson("/api/v1/auth/verify-email", Map.of("email", email, "code", wrongCode)),
                    400, "EMAIL_VERIFICATION_INVALID");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT failed_attempts FROM public.email_verification_challenge", Integer.class))
                    .isEqualTo(attempt);
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT locked_until IS NOT NULL FROM public.email_verification_challenge", Boolean.class)).isTrue();
        MvcResult lockedVerify = postJson("/api/v1/auth/verify-email", Map.of("email", email, "code", correctCode));
        assertProblem(lockedVerify, 429, "EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED");
        assertThat(lockedVerify.getResponse().getHeader("Retry-After")).isEqualTo("900");
        assertProblem(postJson("/api/v1/auth/resend-email-verification", Map.of("email", email)), 429,
                "EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED");

        TEST_CLOCK.advance(Duration.ofMinutes(15));
        assertProblem(postJson("/api/v1/auth/verify-email", Map.of("email", email, "code", correctCode)), 429,
                "EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED");
        assertThat(postJson("/api/v1/auth/resend-email-verification", Map.of("email", email))
                .getResponse().getStatus()).isEqualTo(204);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT failed_attempts FROM public.email_verification_challenge", Integer.class)).isZero();
        String rotatedCode = latestEmailCode();
        assertThat(rotatedCode).isNotEqualTo(correctCode);
        assertProblem(postJson("/api/v1/auth/verify-email", Map.of("email", email, "code", correctCode)), 400,
                "EMAIL_VERIFICATION_INVALID");
        assertThat(postJson("/api/v1/auth/verify-email", Map.of("email", email, "code", rotatedCode))
                .getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    void resendBeforeLockPreservesFailedAttemptCountAndInvalidatesOldCode() throws Exception {
        String email = email();
        register(email);
        String originalCode = latestEmailCode();
        String wrongCode = originalCode.equals("000000") ? "000001" : "000000";
        assertProblem(postJson("/api/v1/auth/verify-email", Map.of("email", email, "code", wrongCode)), 400,
                "EMAIL_VERIFICATION_INVALID");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT failed_attempts FROM public.email_verification_challenge", Integer.class)).isEqualTo(1);

        TEST_CLOCK.advance(Duration.ofSeconds(60));
        assertThat(postJson("/api/v1/auth/resend-email-verification", Map.of("email", email))
                .getResponse().getStatus()).isEqualTo(204);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT failed_attempts FROM public.email_verification_challenge", Integer.class)).isEqualTo(1);
        assertProblem(postJson("/api/v1/auth/verify-email", Map.of("email", email, "code", originalCode)), 400,
                "EMAIL_VERIFICATION_INVALID");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT failed_attempts FROM public.email_verification_challenge", Integer.class)).isEqualTo(2);
    }

    @Test
    void exactExpiryAndMalformedCodeDoNotIncrementAttempts() throws Exception {
        String email = email();
        register(email);
        String code = latestEmailCode();

        assertProblem(postJson("/api/v1/auth/verify-email", Map.of("email", email, "code", "123")), 400,
                "INVALID_REQUEST");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT failed_attempts FROM public.email_verification_challenge", Integer.class)).isZero();
        TEST_CLOCK.advance(Duration.ofMinutes(5));
        assertProblem(postJson("/api/v1/auth/verify-email", Map.of("email", email, "code", code)), 400,
                "EMAIL_VERIFICATION_EXPIRED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT failed_attempts FROM public.email_verification_challenge", Integer.class)).isZero();
    }

    @Test
    void resendCooldownFixedWindowAndNewWindowAreEnforcedInPostgres() throws Exception {
        String email = email();
        register(email);
        String originalCode = latestEmailCode();
        MvcResult cooldown = postJson("/api/v1/auth/resend-email-verification", Map.of("email", email));
        assertProblem(cooldown, 429, "EMAIL_VERIFICATION_SEND_LIMIT_EXCEEDED");
        assertThat(cooldown.getResponse().getHeader("Retry-After")).isEqualTo("60");

        TEST_CLOCK.advance(Duration.ofSeconds(60));
        assertThat(postJson("/api/v1/auth/resend-email-verification", Map.of("email", email))
                .getResponse().getStatus()).isEqualTo(204);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT send_count FROM public.email_verification_challenge", Integer.class)).isEqualTo(2);
        assertProblem(postJson("/api/v1/auth/verify-email", Map.of("email", email, "code", originalCode)), 400,
                "EMAIL_VERIFICATION_INVALID");

        TEST_CLOCK.advance(Duration.ofSeconds(60));
        assertThat(postJson("/api/v1/auth/resend-email-verification", Map.of("email", email))
                .getResponse().getStatus()).isEqualTo(204);
        TEST_CLOCK.advance(Duration.ofSeconds(60));
        MvcResult limited = postJson("/api/v1/auth/resend-email-verification", Map.of("email", email));
        assertProblem(limited, 429, "EMAIL_VERIFICATION_SEND_LIMIT_EXCEEDED");
        assertThat(limited.getResponse().getHeader("Retry-After")).isEqualTo("3420");

        TEST_CLOCK.set(Instant.parse("2026-10-06T01:00:00Z"));
        assertThat(postJson("/api/v1/auth/resend-email-verification", Map.of("email", email))
                .getResponse().getStatus()).isEqualTo(204);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT send_count FROM public.email_verification_challenge", Integer.class)).isEqualTo(1);
    }

    @Test
    void smtpRejectionKeepsRegistrationPendingAndRealSmtpResendRecovers() throws Exception {
        String email = email();
        setMailpitChaos("{\"Recipient\":{\"ErrorCode\":451,\"Probability\":100}}");

        MvcResult failedRegistration = register(email);

        assertProblem(failedRegistration, 503, "EMAIL_DELIVERY_FAILED");
        JsonNode problem = objectMapper.readTree(failedRegistration.getResponse().getContentAsByteArray());
        assertThat(problem.path("account_created").asBoolean()).isTrue();
        assertThat(problem.path("account_status").asText()).isEqualTo("PENDING_VERIFICATION");
        assertThat(problem.path("next_action").asText()).isEqualTo("RESEND_EMAIL_VERIFICATION");
        assertThat(jdbcTemplate.queryForObject("SELECT account_status FROM public.account WHERE email = ?",
                String.class, email)).isEqualTo("PENDING_VERIFICATION");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT send_count FROM public.email_verification_challenge", Integer.class)).isEqualTo(1);

        setMailpitChaos("{}");
        TEST_CLOCK.advance(Duration.ofSeconds(60));
        assertThat(postJson("/api/v1/auth/resend-email-verification", Map.of("email", email))
                .getResponse().getStatus()).isEqualTo(204);
        String recoveredCode = latestEmailCode();
        assertThat(postJson("/api/v1/auth/verify-email", Map.of("email", email, "code", recoveredCode))
                .getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    void simultaneousValidVerificationsActivateOnceAndDuplicateReturnsConflict() throws Exception {
        String email = email();
        register(email);
        String code = latestEmailCode();
        String body = objectMapper.writeValueAsString(Map.of("email", email, "code", code));
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = pool.submit(() -> postAfterBarrier(body, "/api/v1/auth/verify-email", barrier));
            Future<Integer> second = pool.submit(() -> postAfterBarrier(body, "/api/v1/auth/verify-email", barrier));
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(204, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.account WHERE email = ? AND account_status='ACTIVE'",
                Integer.class, email)).isEqualTo(1);
    }

    @Test
    void simultaneousVerifyAndResendCannotConsumeARotatedChallenge() throws Exception {
        String email = email();
        register(email);
        String originalCode = latestEmailCode();
        TEST_CLOCK.advance(Duration.ofSeconds(60));
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        int verifyStatus;
        int resendStatus;
        try {
            Future<Integer> verify = pool.submit(() -> postAfterBarrier(
                    objectMapper.writeValueAsString(Map.of("email", email, "code", originalCode)),
                    "/api/v1/auth/verify-email", barrier));
            Future<Integer> resend = pool.submit(() -> postAfterBarrier(
                    objectMapper.writeValueAsString(Map.of("email", email)),
                    "/api/v1/auth/resend-email-verification", barrier));
            verifyStatus = verify.get(20, TimeUnit.SECONDS);
            resendStatus = resend.get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(List.of(verifyStatus, resendStatus)).contains(204);
        assertThat(List.of(verifyStatus, resendStatus)).allMatch(status -> status == 204 || status == 400 || status == 409);
        if (jdbcTemplate.queryForObject("SELECT account_status FROM public.account WHERE email = ?", String.class, email)
                .equals("PENDING_VERIFICATION")) {
            String currentCode = latestEmailCode();
            assertThat(currentCode).isNotEqualTo(originalCode);
            assertThat(postJson("/api/v1/auth/verify-email", Map.of("email", email, "code", currentCode))
                    .getResponse().getStatus()).isEqualTo(204);
        } else {
            assertThat(verifyStatus).isEqualTo(204);
            assertThat(resendStatus).isEqualTo(409);
        }
    }

    @Test
    void challengeInsertFailureRollsBackAccountAndRole() throws Exception {
        String email = email();
        doThrow(new IllegalStateException("test-only challenge write failure"))
                .when(challengeRepository).insert(any(EmailVerificationChallenge.class));

        MvcResult result = register(email);

        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.account WHERE email = ?",
                Integer.class, email)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.account_role ar JOIN public.account a "
                + "ON a.account_id = ar.account_id WHERE a.email = ?", Integer.class, email)).isZero();
    }

    @Test
    void roleInsertFailureRollsBackAccountBeforeChallengeAndEmail() throws Exception {
        String email = email();
        org.mockito.Mockito.doThrow(new IllegalStateException("test-only role write failure"))
                .when(accountRepository).insertRole(any(UUID.class), org.mockito.ArgumentMatchers.eq("CUSTOMER"));

        MvcResult result = register(email);

        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.account WHERE email = ?",
                Integer.class, email)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM public.email_verification_challenge",
                Integer.class)).isZero();
    }

    @Test
    void accountActivationFailureRollsBackChallengeConsumption() throws Exception {
        String email = email();
        register(email);
        String code = latestEmailCode();
        doThrow(new IllegalStateException("test-only account update failure"))
                .when(accountRepository).activate(any(UUID.class), any());

        MvcResult failure = postJson("/api/v1/auth/verify-email", Map.of("email", email, "code", code));

        assertThat(failure.getResponse().getStatus()).isEqualTo(500);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT consumed_at IS NULL FROM public.email_verification_challenge", Boolean.class)).isTrue();
        org.mockito.Mockito.reset(accountRepository);
        assertThat(postJson("/api/v1/auth/verify-email", Map.of("email", email, "code", code))
                .getResponse().getStatus()).isEqualTo(204);
    }

    private MvcResult register(String email) throws Exception {
        return register(email, "test-password-value-2026");
    }

    private MvcResult register(String email, String password) throws Exception {
        return postJson("/api/v1/auth/register", Map.of("email", email, "password", password));
    }

    private MvcResult postJson(String path, Object body) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andReturn();
    }

    private Integer postAfterBarrier(String body, String path, CyclicBarrier barrier) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus();
    }

    private String latestEmailCode() throws Exception {
        String host = MAILPIT.getHost();
        int port = MAILPIT.getMappedPort(8025);
        assertThat(emailForCurrentTest).as("test recipient is set before reading its verification email")
                .isNotBlank();
        HttpRequest listRequest = HttpRequest.newBuilder(URI.create(
                "http://" + host + ":" + port + "/api/v1/messages?limit=100"))
                .timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<String> listResponse = HttpClient.newHttpClient().send(
                listRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(listResponse.statusCode()).isEqualTo(200);
        JsonNode listedMessages = objectMapper.readTree(listResponse.body());
        JsonNode messages = listedMessages.has("messages")
                ? listedMessages.path("messages") : listedMessages.path("Messages");
        String messageId = null;
        Instant newestMessageAt = Instant.MIN;
        for (JsonNode message : messages) {
            JsonNode recipients = message.has("To") ? message.path("To") : message.path("to");
            boolean addressedToTestAccount = false;
            for (JsonNode recipient : recipients) {
                String address = recipient.has("Address")
                        ? recipient.path("Address").asText() : recipient.path("address").asText();
                addressedToTestAccount |= emailForCurrentTest.equalsIgnoreCase(address);
            }
            if (!addressedToTestAccount) {
                continue;
            }
            String created = message.has("Created")
                    ? message.path("Created").asText() : message.path("created").asText();
            Instant createdAt = Instant.parse(created);
            if (messageId == null || createdAt.isAfter(newestMessageAt)) {
                messageId = message.has("ID") ? message.path("ID").asText() : message.path("id").asText();
                newestMessageAt = createdAt;
            }
        }
        assertThat(messageId).as("Mailpit contains a verification email for this test account").isNotBlank();

        HttpRequest messageRequest = HttpRequest.newBuilder(URI.create(
                "http://" + host + ":" + port + "/api/v1/message/" + messageId))
                .timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<String> messageResponse = HttpClient.newHttpClient().send(
                messageRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(messageResponse.statusCode()).isEqualTo(200);
        JsonNode message = objectMapper.readTree(messageResponse.body());
        String text = message.path("Text").asText(message.path("text").asText());
        String html = message.path("HTML").asText(message.path("html").asText());
        Matcher matcher = SIX_DIGITS.matcher(text + " " + html);
        assertThat(matcher.find()).as("Mailpit must contain a real SMTP verification message").isTrue();
        return matcher.group(1);
    }

    private void setMailpitChaos(String json) throws Exception {
        String host = MAILPIT.getHost();
        int port = MAILPIT.getMappedPort(8025);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + host + ":" + port + "/api/v1/chaos"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json)).build();
        HttpResponse<Void> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isEqualTo(200);
    }

    private void assertProblem(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        JsonNode problem = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(problem.path("code").asText()).isEqualTo(code);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("password_hash", "codeDigest");
    }

    private String email() {
        emailForCurrentTest = "register-" + UUID.randomUUID() + "@example.test";
        return emailForCurrentTest;
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
        @Bean
        @Primary
        Clock integrationClock() {
            return TEST_CLOCK;
        }
    }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;

        MutableClock(Instant instant) {
            this.instant = new AtomicReference<>(instant);
        }

        void set(Instant value) { instant.set(value); }
        void advance(Duration duration) { instant.updateAndGet(value -> value.plus(duration)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant.get(); }
    }
}
