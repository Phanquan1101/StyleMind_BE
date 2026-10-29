package com.stylemind.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.stylemind.identity.entity.Account;
import com.stylemind.identity.entity.ProfileColor;
import com.stylemind.identity.repository.account.AccountRepository;
import com.stylemind.identity.repository.profile.ProfileColorRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.health.db.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class ManageProfilePostgresIntegrationTests {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6")
            .withDatabaseName("stylemind_identity_profile_test")
            .withUsername("identity_profile_test")
            .withPassword("identity_profile_test_only");
    private static final String HMAC_SECRET = JwtTestSupport.randomSecret();
    private static final String SERVICE_TOKEN = JwtTestSupport.randomSecret();
    private static final String ME = "/api/v1/me";
    private static final String STYLE = "/api/v1/me/style-profile";

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
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @LocalServerPort private int port;
    @MockitoSpyBean private AccountRepository accountRepository;
    @MockitoSpyBean private ProfileColorRepository profileColorRepository;

    @BeforeEach
    void clearDisposableProfileDatabase() {
        reset(accountRepository, profileColorRepository);
        jdbc.update("DELETE FROM public.profile_style");
        jdbc.update("DELETE FROM public.profile_color");
        jdbc.update("DELETE FROM public.profile_brand");
        jdbc.update("DELETE FROM public.style_profile");
        jdbc.update("DELETE FROM public.user_profile");
        jdbc.update("DELETE FROM public.email_verification_challenge");
        jdbc.update("DELETE FROM public.password_reset_challenge");
        jdbc.update("DELETE FROM public.refresh_session");
        jdbc.update("DELETE FROM public.account_role");
        jdbc.update("DELETE FROM public.account");
    }

    @Test
    void realHttpJwtSubjectDrivesCustomerAndAdminSelfOnlyProfileAccess() throws Exception {
        UUID owner = account("CUSTOMER", "ADMIN");
        UUID other = account("CUSTOMER");
        jdbc.update("INSERT INTO public.user_profile (account_id, display_name) VALUES (?, ?)", other, "Other User");
        jdbc.update("INSERT INTO public.style_profile (account_id, fit_preference) VALUES (?, ?)", other, "OTHER_FIT");
        jdbc.update("INSERT INTO public.profile_style (account_id, style_code) VALUES (?, ?)", other, "OTHER_STYLE");

        HttpResponse<String> response = sendRealHttp("GET", ME, token(owner, "CUSTOMER"), null, "profile-read-1");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("X-Correlation-Id")).contains("profile-read-1");
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.path("account_id").asText()).isEqualTo(owner.toString());
        assertThat(body.path("email").asText()).isEqualTo(email(owner));
        assertThat(body.path("profile").isNull()).isTrue();
        assertThat(response.body()).doesNotContain("password_hash", "account_status", "role_code", "refresh_session");
        assertThat(count("user_profile", owner)).isZero();

        MvcResult adminSelf = perform("GET", ME + "?account_id=" + other, token(owner, "ADMIN"), null);
        assertThat(adminSelf.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(adminSelf).path("account_id").asText()).isEqualTo(owner.toString());
        MvcResult queryPatch = perform("PATCH", ME + "?account_id=" + other, token(owner, "CUSTOMER"),
                "{\"display_name\":\"Owner Updated\"}");
        assertThat(queryPatch.getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT display_name FROM public.user_profile WHERE account_id = ?",
                String.class, owner)).isEqualTo("Owner Updated");
        assertThat(jdbc.queryForObject("SELECT display_name FROM public.user_profile WHERE account_id = ?",
                String.class, other)).isEqualTo("Other User");
        assertThat(perform("GET", ME + "/" + other, token(owner, "CUSTOMER"), null)
                .getResponse().getStatus()).isEqualTo(404);
        MvcResult ownStyle = perform("GET", STYLE + "?account_id=" + other, token(owner, "CUSTOMER"), null);
        assertThat(ownStyle.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(ownStyle).path("style_profile").isNull()).isTrue();
        assertThat(count("style_profile", owner)).isZero();
        assertThat(jdbc.queryForObject("SELECT fit_preference FROM public.style_profile WHERE account_id = ?",
                String.class, other)).isEqualTo("OTHER_FIT");
        assertThat(jdbc.queryForObject("SELECT style_code FROM public.profile_style WHERE account_id = ?",
                String.class, other)).isEqualTo("OTHER_STYLE");
        MvcResult ownerOnlyReplacement = perform("PUT", STYLE + "?account_id=" + other,
                token(owner, "CUSTOMER"),
                styleBody("\"OWNER_ONLY\"", "null", "null", "null", "null", "[\"OWNER_STYLE\"]", "[]", "[]"));
        assertThat(ownerOnlyReplacement.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(ownerOnlyReplacement).path("style_profile").path("fit_preference").asText())
                .isEqualTo("OWNER_ONLY");
        assertThat(jdbc.queryForObject("SELECT fit_preference FROM public.style_profile WHERE account_id = ?",
                String.class, other)).isEqualTo("OTHER_FIT");
        assertThat(jdbc.queryForObject("SELECT style_code FROM public.profile_style WHERE account_id = ?",
                String.class, other)).isEqualTo("OTHER_STYLE");
    }

    @Test
    void allFourRoutesRequireSignedJwtAndCustomerOrAdminAuthority() throws Exception {
        UUID owner = account("CUSTOMER");
        for (String[] route : List.of(new String[] {"GET", ME}, new String[] {"PATCH", ME},
                new String[] {"GET", STYLE}, new String[] {"PUT", STYLE})) {
            assertThat(perform(route[0], route[1], null, bodyFor(route[0], route[1]))
                    .getResponse().getStatus()).isEqualTo(401);
            assertProblem(perform(route[0], route[1], "not-a-jwt", bodyFor(route[0], route[1])),
                    401, "INVALID_BEARER_TOKEN");
            assertThat(perform(route[0], route[1], token(owner, "STYLIST"), bodyFor(route[0], route[1]))
                    .getResponse().getStatus()).isEqualTo(403);
        }
        assertProblem(perform("GET", ME, token(UUID.randomUUID(), "CUSTOMER"), null), 404, "RESOURCE_NOT_FOUND");
        assertThat(perform("GET", ME, token(owner, "CUSTOMER", Instant.now().minusSeconds(3600)), null)
                .getResponse().getStatus()).isEqualTo(401);

        for (String[] unsupported : List.of(new String[] {"POST", ME}, new String[] {"PUT", ME},
                new String[] {"DELETE", ME}, new String[] {"PATCH", STYLE}, new String[] {"POST", STYLE},
                new String[] {"DELETE", STYLE})) {
            MvcResult result = perform(unsupported[0], unsupported[1], token(owner, "CUSTOMER"), "{}");
            assertThat(result.getResponse().getStatus()).isBetween(400, 499);
            assertThat(count("user_profile", owner)).isZero();
            assertThat(count("style_profile", owner)).isZero();
        }
    }

    @Test
    void runtimeOpenApiDocumentsOnlyTheApprovedProfileContract() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v3/api-docs"))
                        .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode openApi = objectMapper.readTree(response.body());
        assertThat(openApi.path("paths").has(ME)).isTrue();
        assertThat(openApi.path("paths").has(STYLE)).isTrue();
        assertThat(openApi.path("paths").path(ME).has("get")).isTrue();
        assertThat(openApi.path("paths").path(ME).has("patch")).isTrue();
        assertThat(openApi.path("paths").path(STYLE).has("get")).isTrue();
        assertThat(openApi.path("paths").path(STYLE).has("put")).isTrue();
        String operations = openApi.path("paths").path(ME).toString()
                + openApi.path("paths").path(STYLE).toString();
        assertThat(operations).contains("CUSTOMER", "ADMIN", "JWT subject", "existence or active status");
    }

    @Test
    void profileReadsAreReadOnlyAndPatchCreatesThenUpdatesOnlyDisplayName() throws Exception {
        UUID owner = account("CUSTOMER");
        String customer = token(owner, "CUSTOMER");

        MvcResult before = perform("GET", ME, customer, null);
        assertThat(before.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(before).path("profile").isNull()).isTrue();
        assertThat(count("user_profile", owner)).isZero();

        MvcResult created = perform("PATCH", ME, customer, "{\"display_name\":\"  Nguyễn An  \"}");
        assertThat(created.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(created).path("profile").path("display_name").asText()).isEqualTo("Nguyễn An");
        assertThat(count("user_profile", owner)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT email FROM public.account WHERE account_id = ?", String.class, owner))
                .isEqualTo(email(owner));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.account_role WHERE account_id = ?", Integer.class,
                owner)).isEqualTo(1);

        MvcResult updated = perform("PATCH", ME, customer, "{\"display_name\":\"Changed\"}");
        assertThat(json(updated).path("profile").path("display_name").asText()).isEqualTo("Changed");
        assertProblem(perform("PATCH", ME, customer, "{}"), 400, "INVALID_REQUEST");
        assertProblem(perform("PATCH", ME, customer, "{\"display_name\":null}"), 400, "INVALID_REQUEST");
        assertProblem(perform("PATCH", ME, customer, "{\"display_name\":\"  \"}"), 400, "INVALID_REQUEST");
        assertProblem(perform("PATCH", ME, customer,
                "{\"display_name\":\"Must not write\",\"account_id\":\"" + UUID.randomUUID() + "\","
                        + "\"email\":\"forged@example.test\",\"role\":\"ADMIN\",\"account_status\":\"ACTIVE\"}"),
                400, "INVALID_REQUEST");
        MvcResult malformed = perform("PATCH", ME, customer, "{");
        assertProblem(malformed, 400, "INVALID_REQUEST");
        assertThat(malformed.getResponse().getHeader("X-Correlation-Id")).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT display_name FROM public.user_profile WHERE account_id = ?",
                String.class, owner)).isEqualTo("Changed");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.account WHERE account_id = ? AND email = ?",
                Integer.class, owner, email(owner))).isEqualTo(1);
    }

    @Test
    void styleGetDistinguishesAbsentAndEmptyAndPutReplacesTheWholeAggregate() throws Exception {
        UUID owner = account("CUSTOMER");
        String customer = token(owner, "CUSTOMER");

        MvcResult absent = perform("GET", STYLE, customer, null);
        assertThat(json(absent).path("style_profile").isNull()).isTrue();
        assertThat(count("style_profile", owner)).isZero();

        jdbc.update("INSERT INTO public.style_profile (account_id) VALUES (?)", owner);
        JsonNode empty = json(perform("GET", STYLE, customer, null)).path("style_profile");
        assertThat(empty.path("fit_preference").isNull()).isTrue();
        assertThat(empty.path("height_cm").isNull()).isTrue();
        assertThat(empty.path("weight_kg").isNull()).isTrue();
        assertThat(empty.path("usual_top_size").isNull()).isTrue();
        assertThat(empty.path("usual_bottom_size").isNull()).isTrue();
        assertThat(empty.path("shoe_size").isNull()).isTrue();
        assertThat(empty.path("style_codes").isArray() && empty.path("style_codes").isEmpty()).isTrue();
        assertThat(empty.path("color_codes").isArray() && empty.path("color_codes").isEmpty()).isTrue();
        assertThat(empty.path("brand_ids").isArray() && empty.path("brand_ids").isEmpty()).isTrue();

        UUID unknownBrand = UUID.randomUUID();
        String replacement = styleBody("\"relaxed\"", "170.5", "\"  M  \"", "null", "\"EU 39\"",
                "[\"zeta\",\"alpha\"]", "[\"Blue\",\"black\"]", "[\"" + unknownBrand + "\"]")
                .replace("\"weight_kg\":null", "\"weight_kg\":65.5");
        MvcResult put = perform("PUT", STYLE, customer, replacement);
        assertThat(put.getResponse().getStatus()).isEqualTo(200);
        JsonNode saved = json(put).path("style_profile");
        assertThat(saved.path("fit_preference").asText()).isEqualTo("RELAXED");
        assertThat(saved.path("height_cm").decimalValue()).isEqualByComparingTo("170.5");
        assertThat(saved.path("weight_kg").decimalValue()).isEqualByComparingTo("65.5");
        assertThat(jdbc.queryForObject("SELECT weight_kg FROM public.style_profile WHERE account_id = ?",
                java.math.BigDecimal.class, owner)).isEqualByComparingTo("65.5");
        assertThat(saved.path("usual_top_size").asText()).isEqualTo("M");
        assertThat(saved.path("usual_bottom_size").isNull()).isTrue();
        assertThat(saved.path("shoe_size").asText()).isEqualTo("EU 39");
        assertThat(strings(saved.path("style_codes"))).containsExactly("ALPHA", "ZETA");
        assertThat(strings(saved.path("color_codes"))).containsExactly("BLACK", "BLUE");
        assertThat(strings(saved.path("brand_ids"))).containsExactly(unknownBrand.toString());
        assertThat(count("profile_brand", owner)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT brand_id FROM public.profile_brand WHERE account_id = ?", UUID.class,
                owner)).isEqualTo(unknownBrand);

        MvcResult omittedWeight = perform("PUT", STYLE, customer,
                styleBody("null", "170", "null", "null", "null", "[]", "[]", "[]").replace("\"weight_kg\":null,", ""));
        assertThat(omittedWeight.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(omittedWeight).path("style_profile").path("weight_kg").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT weight_kg FROM public.style_profile WHERE account_id = ?",
                java.math.BigDecimal.class, owner)).isNull();

        MvcResult cleared = perform("PUT", STYLE, customer,
                styleBody("null", "null", "null", "null", "null", "[]", "[]", "[]"));
        JsonNode clearedProfile = json(cleared).path("style_profile");
        assertThat(clearedProfile.path("fit_preference").isNull()).isTrue();
        assertThat(clearedProfile.path("height_cm").isNull()).isTrue();
        assertThat(clearedProfile.path("weight_kg").isNull()).isTrue();
        assertThat(strings(clearedProfile.path("style_codes"))).isEmpty();
        assertThat(strings(clearedProfile.path("color_codes"))).isEmpty();
        assertThat(strings(clearedProfile.path("brand_ids"))).isEmpty();
        assertThat(count("style_profile", owner)).isEqualTo(1);
        assertThat(count("profile_style", owner)).isZero();
        assertThat(count("profile_color", owner)).isZero();
        assertThat(count("profile_brand", owner)).isZero();

        UUID emptyOwner = account("CUSTOMER");
        MvcResult emptyCreate = perform("PUT", STYLE, token(emptyOwner, "CUSTOMER"),
                styleBody("null", "null", "null", "null", "null", "[]", "[]", "[]"));
        assertThat(emptyCreate.getResponse().getStatus()).isEqualTo(200);
        JsonNode emptyCreatedProfile = json(emptyCreate).path("style_profile");
        assertThat(emptyCreatedProfile.path("fit_preference").isNull()).isTrue();
        assertThat(emptyCreatedProfile.path("height_cm").isNull()).isTrue();
        assertThat(emptyCreatedProfile.path("weight_kg").isNull()).isTrue();
        assertThat(strings(emptyCreatedProfile.path("style_codes"))).isEmpty();
        assertThat(strings(emptyCreatedProfile.path("color_codes"))).isEmpty();
        assertThat(strings(emptyCreatedProfile.path("brand_ids"))).isEmpty();
        assertThat(count("style_profile", emptyOwner)).isEqualTo(1);
    }

    @Test
    void invalidStyleInputAndMissingAccountNeverLeavePartialRows() throws Exception {
        UUID owner = account("CUSTOMER");
        String customer = token(owner, "CUSTOMER");
        for (String invalid : List.of(
                "{}",
                styleBody("null", "null", "null", "null", "null", "[]", "[]", "[]")
                        .replace(",\"brand_ids\":[]", ""),
                styleBody("null", "null", "null", "null", "null", "null", "[]", "[]"),
                styleBody("null", "null", "null", "null", "null", "[]", "[]", "[]")
                        .replace("\"height_cm\":null", "\"height_cm\":\"170\""),
                styleBody("null", "null", "null", "null", "null", "[]", "[]", "[]")
                        .replace("\"style_codes\":[]", "\"style_codes\":[\"casual\",\"CASUAL\"]"),
                styleBody("null", "null", "null", "null", "null", "[]", "[]", "[]")
                        .replace("\"brand_ids\":[]", "\"brand_ids\":[\"invalid\"]"),
                styleBody("null", "251", "null", "null", "null", "[]", "[]", "[]"),
                styleBody("null", "null", "null", "null", "null", "[]", "[]", "[]")
                        .replace("\"weight_kg\":null", "\"weight_kg\":\"70\""),
                styleBody("null", "null", "null", "null", "null", "[]", "[]", "[]")
                        .replace("\"weight_kg\":null", "\"weight_kg\":19.9"),
                styleBody("null", "null", "null", "null", "null", "[]", "[]", "[]")
                        .replace("\"weight_kg\":null", "\"weight_kg\":300.1"),
                styleBody("null", "null", "null", "null", "null", "[]", "[]", "[]")
                        .replace("\"color_codes\":[]", "\"color_codes\":null"),
                styleBody("null", "null", "null", "null", "null", "[]", "[]", "[]")
                        .replace("\"shoe_size\":null", "\"shoe_size\":null,\"unexpected\":true"))) {
            assertProblem(perform("PUT", STYLE, customer, invalid), 400, "INVALID_REQUEST");
            assertThat(count("style_profile", owner)).isZero();
            assertThat(count("profile_style", owner)).isZero();
            assertThat(count("profile_color", owner)).isZero();
            assertThat(count("profile_brand", owner)).isZero();
        }
        UUID nonexistent = UUID.randomUUID();
        assertProblem(perform("PUT", STYLE, token(nonexistent, "CUSTOMER"),
                styleBody("null", "null", "null", "null", "null", "[]", "[]", "[]")),
                404, "RESOURCE_NOT_FOUND");
        assertThat(count("style_profile", nonexistent)).isZero();
    }

    @Test
    void failureAfterScalarAndCollectionWritesRollsBackTheWholeReplacement() throws Exception {
        UUID owner = account("CUSTOMER");
        String customer = token(owner, "CUSTOMER");
        assertThat(perform("PUT", STYLE, customer,
                styleBody("\"SLIM\"", "160", "\"S\"", "null", "null",
                        "[\"OLD\"]", "[\"RED\"]", "[]")).getResponse().getStatus()).isEqualTo(200);

        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("test-only profile write failure");
        }).when(profileColorRepository).saveAll(any(Iterable.class));

        MvcResult failure;
        try {
            failure = perform("PUT", STYLE, customer,
                    styleBody("\"LOOSE\"", "180", "\"L\"", "\"32\"", "\"EU 42\"",
                            "[\"NEW\"]", "[\"BLUE\"]", "[]"));
        } finally {
            reset(accountRepository, profileColorRepository);
        }
        assertProblem(failure, 500, "INTERNAL_ERROR");
        JsonNode profile = json(perform("GET", STYLE, customer, null)).path("style_profile");
        assertThat(profile.path("fit_preference").asText()).isEqualTo("SLIM");
        assertThat(profile.path("height_cm").decimalValue()).isEqualByComparingTo("160");
        assertThat(strings(profile.path("style_codes"))).containsExactly("OLD");
        assertThat(strings(profile.path("color_codes"))).containsExactly("RED");
    }

    @Test
    void concurrentFirstCreateAndConcurrentPutSerializeOnOwnerAccount() throws Exception {
        UUID owner = account("CUSTOMER");
        String customer = token(owner, "CUSTOMER");
        assertSerializedWrites(owner, customer, "PATCH", ME,
                "{\"display_name\":\"First\"}", "{\"display_name\":\"Second\"}");
        assertThat(count("user_profile", owner)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT display_name FROM public.user_profile WHERE account_id = ?",
                String.class, owner)).isEqualTo("Second");

        assertSerializedWrites(owner, customer, "PUT", STYLE,
                styleBody("\"FIRST\"", "160", "null", "null", "null", "[\"FIRST\"]", "[]", "[]"),
                styleBody("\"SECOND\"", "190", "\"L\"", "null", "null", "[\"SECOND\"]", "[\"BLUE\"]", "[]"));
        JsonNode finalProfile = json(perform("GET", STYLE, customer, null)).path("style_profile");
        assertThat(finalProfile.path("fit_preference").asText()).isEqualTo("SECOND");
        assertThat(finalProfile.path("height_cm").decimalValue()).isEqualByComparingTo("190");
        assertThat(finalProfile.path("usual_top_size").asText()).isEqualTo("L");
        assertThat(strings(finalProfile.path("style_codes"))).containsExactly("SECOND");
        assertThat(strings(finalProfile.path("color_codes"))).containsExactly("BLUE");
        assertThat(count("style_profile", owner)).isEqualTo(1);
    }

    private void assertSerializedWrites(UUID owner, String bearer, String method, String path,
            String firstBody, String secondBody) throws Exception {
        reset(accountRepository, profileColorRepository);
        CountDownLatch firstOwnsLock = new CountDownLatch(1);
        CountDownLatch secondReachedLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger order = new AtomicInteger();
        doAnswer(invocation -> {
            int position = order.incrementAndGet();
            if (position == 1) {
                boolean locked = (boolean) invocation.callRealMethod();
                firstOwnsLock.countDown();
                if (!releaseFirst.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test synchronization timed out");
                }
                return locked;
            }
            if (position == 2) {
                secondReachedLock.countDown();
            }
            return invocation.callRealMethod();
        }).when(accountRepository).findLockedByAccountId(eq(owner));

        CompletableFuture<HttpResponse<String>> first = sendAsync(method, path, bearer, firstBody);
        assertThat(firstOwnsLock.await(10, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<HttpResponse<String>> second = sendAsync(method, path, bearer, secondBody);
        try {
            assertThat(secondReachedLock.await(10, TimeUnit.SECONDS)).isTrue();
            awaitPostgresRowLockWait();
        } finally {
            releaseFirst.countDown();
        }
        assertThat(first.get(15, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
        assertThat(second.get(15, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
            reset(accountRepository, profileColorRepository);
    }

    private void awaitPostgresRowLockWait() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity "
                    + "WHERE datname = current_database() AND state = 'active' "
                    + "AND wait_event_type = 'Lock' AND query ILIKE '%FOR UPDATE%'", Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Concurrent request did not block on the owner row lock in PostgreSQL");
    }

    private UUID account(String... roles) {
        UUID accountId = UUID.randomUUID();
        jdbc.update("INSERT INTO public.account (account_id, email, password_hash, account_status, registered_at) "
                + "VALUES (?, ?, ?, 'ACTIVE', now())", accountId, email(accountId), "test-only-hash");
        for (String role : roles) {
            jdbc.update("INSERT INTO public.account_role (account_id, role_code) VALUES (?, ?)", accountId, role);
        }
        return accountId;
    }

    private String token(UUID subject, String role) throws Exception {
        return token(subject, role, Instant.now().plusSeconds(600));
    }

    private String token(UUID subject, String role, Instant expiresAt) throws Exception {
        Instant now = Instant.now();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(JwtTestSupport.SIGNING_KEY.getKeyID()).build(),
                new JWTClaimsSet.Builder().issuer(JwtTestSupport.ISSUER).audience(JwtTestSupport.AUDIENCE)
                        .subject(subject.toString()).jwtID(UUID.randomUUID().toString())
                        .issueTime(Date.from(now.minusSeconds(1))).expirationTime(Date.from(expiresAt))
                        .claim("roles", List.of(role)).build());
        jwt.sign(new RSASSASigner(JwtTestSupport.SIGNING_KEY.toPrivateKey()));
        return jwt.serialize();
    }

    private HttpResponse<String> sendRealHttp(String method, String path, String bearer, String body,
            String correlationId) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + bearer)
                .header("X-Correlation-Id", correlationId);
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private CompletableFuture<HttpResponse<String>> sendAsync(String method, String path, String bearer,
            String body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + bearer)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        return HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    private MvcResult perform(String method, String path, String bearer, String body) throws Exception {
        var requestBuilder = request(HttpMethod.valueOf(method), path);
        if (bearer != null) {
            requestBuilder.header("Authorization", "Bearer " + bearer);
        }
        if (body != null) {
            requestBuilder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(requestBuilder).andReturn();
    }

    private String bodyFor(String method, String path) {
        return "PATCH".equals(method) ? "{\"display_name\":\"No write\"}"
                : "PUT".equals(method) ? styleBody("null", "null", "null", "null", "null", "[]", "[]", "[]")
                : null;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private List<String> strings(JsonNode array) {
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    private void assertProblem(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        JsonNode problem = json(result);
        assertThat(problem.path("code").asText()).isEqualTo(code);
        assertThat(result.getResponse().getContentType()).contains("application/problem+json");
    }

    private int count(String table, UUID accountId) {
        return jdbc.queryForObject("SELECT count(*) FROM public." + table + " WHERE account_id = ?",
                Integer.class, accountId);
    }

    private static String email(UUID accountId) {
        return "profile-" + accountId + "@example.test";
    }

    private static String styleBody(String fit, String height, String top, String bottom, String shoe,
            String styles, String colors, String brands) {
        return "{\"fit_preference\":" + fit + ",\"height_cm\":" + height + ",\"weight_kg\":null,\"usual_top_size\":" + top
                + ",\"usual_bottom_size\":" + bottom + ",\"shoe_size\":" + shoe
                + ",\"style_codes\":" + styles + ",\"color_codes\":" + colors + ",\"brand_ids\":" + brands + "}";
    }
}
