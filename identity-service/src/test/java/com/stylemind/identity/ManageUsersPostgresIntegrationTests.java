package com.stylemind.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.stylemind.identity.repository.users.ManageUsersRepository;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.health.db.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class ManageUsersPostgresIntegrationTests {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6")
            .withDatabaseName("stylemind_identity_manage_users_test")
            .withUsername("identity_manage_users_test")
            .withPassword("test-only-not-a-secret");
    private static final String HMAC_SECRET = JwtTestSupport.randomSecret();
    private static final String SERVICE_TOKEN = JwtTestSupport.randomSecret();
    private static final String USERS = "/api/v1/admin/users";
    private static final String TEST_PASSWORD = UUID.randomUUID() + "-TestOnly!";

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

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @LocalServerPort private int port;
    @MockitoSpyBean private ManageUsersRepository repository;

    @BeforeEach
    void clearDisposableDatabase() {
        reset(repository);
        jdbc.update("DELETE FROM public.profile_style");
        jdbc.update("DELETE FROM public.profile_color");
        jdbc.update("DELETE FROM public.profile_brand");
        jdbc.update("DELETE FROM public.style_profile");
        jdbc.update("DELETE FROM public.customer_address");
        jdbc.update("DELETE FROM public.user_profile");
        jdbc.update("DELETE FROM public.email_verification_challenge");
        jdbc.update("DELETE FROM public.password_reset_challenge");
        jdbc.update("DELETE FROM public.refresh_session");
        jdbc.update("DELETE FROM public.account_role");
        jdbc.update("DELETE FROM public.account");
    }

    @Test
    void listFiltersLiteralEmailStablePagesCompleteRolesAndSafeOutput() throws Exception {
        User admin = account("ACTIVE", true, false, "ADMIN");
        User dualRole = accountWithEmail("ACTIVE", true, false,
                "literal%_match-" + UUID.randomUUID() + "@example.test", "ADMIN", "CUSTOMER");
        User customer = account("ACTIVE", true, false, "CUSTOMER");
        OffsetDateTime sameTime = OffsetDateTime.parse("2026-01-02T03:04:05Z");
        for (User user : List.of(admin, dualRole, customer)) {
            jdbc.update("UPDATE public.account SET registered_at = ? WHERE account_id = ?", sameTime, user.id());
        }
        String bearer = token(admin.id(), "ADMIN");

        HttpResponse<String> allResponse = send("GET", USERS, bearer, null);
        assertThat(allResponse.statusCode()).isEqualTo(200);
        assertNoStore(allResponse);
        JsonNode all = json(allResponse);
        assertThat(all.path("page").asInt()).isZero();
        assertThat(all.path("size").asInt()).isEqualTo(20);
        assertThat(all.path("total_elements").asLong()).isEqualTo(3);
        assertThat(all.path("total_pages").asLong()).isEqualTo(1);
        assertThat(all.path("items").size()).isEqualTo(3);
        List<UUID> actualOrder = new ArrayList<>();
        all.path("items").forEach(item -> actualOrder.add(UUID.fromString(item.path("account_id").asText())));
        List<UUID> dbOrder = jdbc.queryForList(
                "SELECT account_id FROM public.account ORDER BY registered_at DESC, account_id ASC", UUID.class);
        assertThat(actualOrder).containsExactlyElementsOf(dbOrder);
        JsonNode dual = findItem(all, dualRole.id());
        assertThat(strings(dual.path("roles"))).containsExactly("ADMIN", "CUSTOMER");
        assertThat(allResponse.body()).doesNotContain("password_hash", "token_hash", "refresh_session",
                "challenge", "display_name", "address_id", "account_role");

        HttpResponse<String> roleFilter = send("GET", USERS + "?role=ADMIN", bearer, null);
        JsonNode rolePage = json(roleFilter);
        assertThat(rolePage.path("total_elements").asLong()).isEqualTo(2);
        assertThat(strings(findItem(rolePage, dualRole.id()).path("roles")))
                .containsExactly("ADMIN", "CUSTOMER");

        HttpResponse<String> statusFilter = send("GET", USERS + "?status=ACTIVE", bearer, null);
        assertThat(json(statusFilter).path("total_elements").asLong()).isEqualTo(3);

        String emailPart = URLEncoder.encode("LITERAL%_MATCH-", StandardCharsets.UTF_8);
        HttpResponse<String> literalFilter = send("GET", USERS + "?email_contains=" + emailPart, bearer, null);
        JsonNode literalPage = json(literalFilter);
        assertThat(literalPage.path("total_elements").asLong()).isEqualTo(1);
        assertThat(literalPage.path("items").get(0).path("account_id").asText()).isEqualTo(dualRole.id().toString());

        HttpResponse<String> combined = send("GET",
                USERS + "?status=ACTIVE&role=ADMIN&email_contains=" + emailPart, bearer, null);
        assertThat(json(combined).path("total_elements").asLong()).isEqualTo(1);

        HttpResponse<String> first = send("GET", USERS + "?size=1&page=0", bearer, null);
        HttpResponse<String> second = send("GET", USERS + "?size=1&page=1", bearer, null);
        assertThat(json(first).path("total_pages").asLong()).isEqualTo(3);
        assertThat(json(second).path("items").size()).isEqualTo(1);
        assertThat(json(first).path("items").get(0).path("account_id").asText())
                .isNotEqualTo(json(second).path("items").get(0).path("account_id").asText());

        HttpResponse<String> empty = send("GET", USERS + "?email_contains=missing-user", bearer, null);
        assertThat(json(empty).path("items")).isEmpty();
        assertThat(json(empty).path("total_elements").asLong()).isZero();
        assertThat(json(empty).path("total_pages").asLong()).isZero();

        for (String query : List.of("?page=-1", "?size=101", "?status=LOCKED", "?role=admin",
                "?unknown=x", "?account_id=" + customer.id(), "?role=ADMIN&role=CUSTOMER",
                "?email_contains=%20%20")) {
            HttpResponse<String> invalid = send("GET", USERS + query, bearer, null);
            assertProblem(invalid, 400, "INVALID_REQUEST");
        }
    }

    @Test
    void openApiPublishesOnlyTheThreeManageUsersOperations() throws Exception {
        HttpResponse<String> response = send("GET", "/v3/api-docs", null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode paths = json(response).path("paths");
        assertThat(paths.path(USERS).has("get")).isTrue();
        assertThat(paths.path(USERS + "/{accountId}/status").has("patch")).isTrue();
        assertThat(paths.path(USERS + "/{accountId}/roles").has("put")).isTrue();
        assertThat(paths.path(USERS + "/{accountId}/status").has("delete")).isFalse();
        assertThat(paths.path(USERS + "/{accountId}/roles").has("post")).isFalse();
    }

    @Test
    void realHttpAuthorizationCoversLiveActorHeadEncodedAliasesAndUnsupportedMethods() throws Exception {
        User admin = account("ACTIVE", true, false, "ADMIN");
        User customer = account("ACTIVE", true, false, "CUSTOMER");
        User target = account("ACTIVE", true, false, "CUSTOMER");
        User suspendedAdmin = account("SUSPENDED", true, false, "ADMIN");
        User unverifiedAdmin = account("ACTIVE", false, false, "ADMIN");
        User removedAdmin = account("ACTIVE", true, false, "ADMIN");
        User missing = new User(UUID.randomUUID(), "missing@example.test");

        List<Route> routes = List.of(
                new Route("GET", USERS, null),
                new Route("PATCH", USERS + "/" + target.id() + "/status", "{\"account_status\":\"SUSPENDED\"}"),
                new Route("PUT", USERS + "/" + target.id() + "/roles", "{\"roles\":[\"ADMIN\"]}"));
        String adminToken = token(admin.id(), "ADMIN");
        String customerToken = token(customer.id(), "CUSTOMER");

        clearInvocations(repository);
        for (Route route : routes) {
            assertProblem(send(route.method(), route.path(), null, route.body()), 401, "AUTHENTICATION_REQUIRED");
            assertProblem(send(route.method(), route.path(), "not-a-jwt", route.body()), 401,
                    "INVALID_BEARER_TOKEN");
            assertProblem(send(route.method(), route.path(),
                    token(admin.id(), List.of("ADMIN"), Instant.now().minusSeconds(1),
                            JwtTestSupport.ISSUER, JwtTestSupport.AUDIENCE), route.body()),
                    401, "INVALID_BEARER_TOKEN");
            assertProblem(send(route.method(), route.path(), tamper(adminToken), route.body()),
                    401, "INVALID_BEARER_TOKEN");
            assertProblem(send(route.method(), route.path(),
                    token(admin.id(), List.of("ADMIN"), Instant.now().plusSeconds(600),
                            "https://wrong.stylemind.local", JwtTestSupport.AUDIENCE), route.body()),
                    401, "INVALID_BEARER_TOKEN");
            assertProblem(send(route.method(), route.path(),
                    token(admin.id(), List.of("ADMIN"), Instant.now().plusSeconds(600),
                            JwtTestSupport.ISSUER, "wrong-audience"), route.body()),
                    401, "INVALID_BEARER_TOKEN");
            assertProblem(send(route.method(), route.path(), customerToken, route.body()), 403, "ACCESS_DENIED");
            assertProblem(send(route.method(), route.path(), token(admin.id(), "STYLIST"), route.body()),
                    403, "ACCESS_DENIED");
        }
        org.mockito.Mockito.verifyNoInteractions(repository);

        String unsupportedRole = token(admin.id(), "STYLIST");
        assertProblem(send("GET", USERS, unsupportedRole, null), 403, "ACCESS_DENIED");
        assertProblem(send("GET", USERS, token(missing.id(), "ADMIN"), null), 403, "ADMIN_ACCESS_DENIED");
        assertProblem(send("GET", USERS, token(suspendedAdmin.id(), "ADMIN"), null), 403, "ADMIN_ACCESS_DENIED");
        assertProblem(send("GET", USERS, token(unverifiedAdmin.id(), "ADMIN"), null), 403, "ADMIN_ACCESS_DENIED");
        jdbc.update("DELETE FROM public.account_role WHERE account_id = ? AND role_code = 'ADMIN'", removedAdmin.id());
        String staleAdmin = token(removedAdmin.id(), "ADMIN");
        for (Route route : routes) {
            assertProblem(send(route.method(), route.path(), staleAdmin, route.body()), 403, "ADMIN_ACCESS_DENIED");
        }
        assertThat(roles(target.id())).containsExactly("CUSTOMER");

        HttpResponse<String> missingHead = send("HEAD", USERS, null, null);
        assertThat(missingHead.statusCode()).isEqualTo(401);
        assertNoStore(missingHead);
        HttpResponse<String> customerHead = send("HEAD", USERS, customerToken, null);
        assertThat(customerHead.statusCode()).isEqualTo(403);
        assertNoStore(customerHead);
        HttpResponse<String> head = send("HEAD", USERS, adminToken, null);
        assertThat(head.statusCode()).isEqualTo(200);
        assertThat(head.body()).isEmpty();
        assertNoStore(head);

        String encodedUsers = "/api/v1/admin/%75sers";
        assertProblem(send("GET", encodedUsers, null, null), 401, "AUTHENTICATION_REQUIRED");
        assertThat(send("GET", encodedUsers, customerToken, null).statusCode()).isEqualTo(403);
        assertThat(send("GET", encodedUsers, adminToken, null).statusCode()).isEqualTo(200);
        String encodedStatus = USERS + "/" + target.id() + "/%73tatus";
        assertProblem(send("PATCH", encodedStatus, null, "{\"account_status\":\"SUSPENDED\"}"),
                401, "AUTHENTICATION_REQUIRED");
        assertThat(send("PATCH", encodedStatus, customerToken, "{\"account_status\":\"SUSPENDED\"}")
                .statusCode()).isEqualTo(403);
        assertThat(send("PATCH", encodedStatus, adminToken, "{\"account_status\":\"ACTIVE\"}")
                .statusCode()).isEqualTo(200);

        clearInvocations(repository);
        assertThat(send("OPTIONS", USERS, null, null).statusCode()).isEqualTo(401);
        assertThat(send("OPTIONS", USERS, customerToken, null).statusCode()).isEqualTo(403);
        HttpResponse<String> options = send("OPTIONS", USERS, adminToken, null);
        assertThat(options.statusCode()).isEqualTo(200);
        assertThat(options.headers().firstValue("Allow").orElse("")).contains("GET", "HEAD", "OPTIONS")
                .doesNotContain("PATCH", "PUT");
        for (String method : List.of("POST", "DELETE", "PATCH")) {
            HttpResponse<String> unsupported = send(method, USERS, adminToken, "{}");
            assertThat(unsupported.statusCode()).isBetween(400, 499);
        }
        org.mockito.Mockito.verifyNoInteractions(repository);
        assertThat(send("POST", USERS, null, "{}").statusCode()).isEqualTo(401);
        assertThat(send("POST", USERS, customerToken, "{}").statusCode()).isEqualTo(403);
        org.mockito.Mockito.verifyNoInteractions(repository);
    }

    @Test
    void statusRoleNoOpsSessionEffectsAndLastAdminGuardsFollowApprovedRules() throws Exception {
        User adminA = account("ACTIVE", true, false, "ADMIN", "CUSTOMER");
        User adminB = account("ACTIVE", true, false, "ADMIN");
        User customer = account("ACTIVE", true, true, "CUSTOMER");
        User pending = account("PENDING_VERIFICATION", false, false, "CUSTOMER");
        User suspendedUnverified = account("SUSPENDED", false, false, "CUSTOMER");
        String adminAToken = token(adminA.id(), "ADMIN");
        String adminBToken = token(adminB.id(), "ADMIN");

        HttpResponse<String> statusNoOp = send("PATCH", USERS + "/" + adminA.id() + "/status",
                adminAToken, "{\"account_status\":\"ACTIVE\"}");
        assertThat(statusNoOp.statusCode()).isEqualTo(200);
        assertNoStore(statusNoOp);
        HttpResponse<String> rolesNoOp = send("PUT", USERS + "/" + adminA.id() + "/roles",
                adminAToken, "{\"roles\":[\"CUSTOMER\",\"ADMIN\"]}");
        assertThat(rolesNoOp.statusCode()).isEqualTo(200);
        assertThat(strings(json(rolesNoOp).path("roles"))).containsExactly("ADMIN", "CUSTOMER");

        assertProblem(send("PATCH", USERS + "/" + adminA.id() + "/status", adminAToken,
                "{\"account_status\":\"SUSPENDED\"}"), 409, "ADMIN_SELF_CHANGE_FORBIDDEN");
        assertProblem(send("PUT", USERS + "/" + adminA.id() + "/roles", adminAToken,
                "{\"roles\":[\"CUSTOMER\"]}"), 409, "ADMIN_SELF_CHANGE_FORBIDDEN");

        for (String state : List.of("ACTIVE", "SUSPENDED")) {
            assertProblem(send("PATCH", USERS + "/" + pending.id() + "/status", adminAToken,
                    "{\"account_status\":\"" + state + "\"}"), 409, "ACCOUNT_STATUS_TRANSITION_NOT_ALLOWED");
        }
        assertProblem(send("PATCH", USERS + "/" + suspendedUnverified.id() + "/status", adminAToken,
                "{\"account_status\":\"ACTIVE\"}"), 409, "ACCOUNT_STATUS_TRANSITION_NOT_ALLOWED");
        assertProblem(send("PATCH", USERS + "/" + UUID.randomUUID() + "/status", adminAToken,
                "{\"account_status\":\"SUSPENDED\"}"), 404, "RESOURCE_NOT_FOUND");
        assertProblem(send("PATCH", USERS + "/" + customer.id() + "/status", adminAToken,
                "{\"account_status\":\"PENDING_VERIFICATION\"}"), 400, "INVALID_REQUEST");
        assertProblem(send("PUT", USERS + "/" + customer.id() + "/roles", adminAToken,
                "{\"roles\":[]}"), 400, "INVALID_REQUEST");

        HttpResponse<String> login = login(customer);
        assertThat(login.statusCode()).isEqualTo(200);
        JsonNode loginBody = json(login);
        String oldAccess = loginBody.path("access_token").asText();
        String refresh = loginBody.path("refresh_token").asText();
        OffsetDateTime verifiedAt = verifiedAt(customer.id());
        String originalPasswordHash = passwordHash(customer.id());
        int originalSessions = sessionCount(customer.id());

        HttpResponse<String> suspended = send("PATCH", USERS + "/" + customer.id() + "/status",
                adminAToken, "{\"account_status\":\"SUSPENDED\"}");
        assertThat(suspended.statusCode()).isEqualTo(200);
        assertThat(json(suspended).path("account_status").asText()).isEqualTo("SUSPENDED");
        assertThat(verifiedAt(customer.id())).isEqualTo(verifiedAt);
        assertThat(passwordHash(customer.id())).isEqualTo(originalPasswordHash);
        assertThat(sessionCount(customer.id())).isEqualTo(originalSessions);
        assertThat(activeSessionCount(customer.id())).isEqualTo(originalSessions);
        assertProblem(login(customer), 403, "ACCOUNT_NOT_ACTIVE");
        assertProblem(send("POST", "/api/v1/auth/refresh", null,
                "{\"refresh_token\":\"" + refresh + "\"}"), 403, "ACCOUNT_NOT_ACTIVE");
        assertThat(send("GET", "/api/v1/me", oldAccess, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/v1/me/addresses", oldAccess, null).statusCode()).isEqualTo(200);

        HttpResponse<String> reactivated = send("PATCH", USERS + "/" + customer.id() + "/status",
                adminAToken, "{\"account_status\":\"ACTIVE\"}");
        assertThat(reactivated.statusCode()).isEqualTo(200);
        assertThat(verifiedAt(customer.id())).isEqualTo(verifiedAt);
        HttpResponse<String> resumedRefresh = send("POST", "/api/v1/auth/refresh", null,
                "{\"refresh_token\":\"" + refresh + "\"}");
        assertThat(resumedRefresh.statusCode()).isEqualTo(200);
        String afterReactivationAccess = json(resumedRefresh).path("access_token").asText();
        String currentRefresh = json(resumedRefresh).path("refresh_token").asText();
        assertThat(jwtRoles(afterReactivationAccess)).containsExactly("CUSTOMER");

        HttpResponse<String> granted = send("PUT", USERS + "/" + customer.id() + "/roles",
                adminAToken, "{\"roles\":[\"ADMIN\",\"CUSTOMER\"]}");
        assertThat(granted.statusCode()).isEqualTo(200);
        assertThat(strings(json(granted).path("roles"))).containsExactly("ADMIN", "CUSTOMER");
        assertThat(jwtRoles(afterReactivationAccess)).containsExactly("CUSTOMER");
        assertThat(send("GET", USERS, afterReactivationAccess, null).statusCode()).isEqualTo(403);

        HttpResponse<String> newRoleRefresh = send("POST", "/api/v1/auth/refresh", null,
                "{\"refresh_token\":\"" + currentRefresh + "\"}");
        assertThat(newRoleRefresh.statusCode()).isEqualTo(200);
        String customerStaleAdminJwt = json(newRoleRefresh).path("access_token").asText();
        String refreshAfterRoleIssue = json(newRoleRefresh).path("refresh_token").asText();
        assertThat(jwtRoles(customerStaleAdminJwt)).containsExactly("ADMIN", "CUSTOMER");
        assertThat(send("GET", USERS, customerStaleAdminJwt, null).statusCode()).isEqualTo(200);

        int sessionsBeforeRoleRemoval = sessionCount(customer.id());
        HttpResponse<String> removed = send("PUT", USERS + "/" + customer.id() + "/roles",
                adminAToken, "{\"roles\":[\"CUSTOMER\"]}");
        assertThat(removed.statusCode()).isEqualTo(200);
        assertThat(roles(customer.id())).containsExactly("CUSTOMER");
        assertThat(sessionCount(customer.id())).isEqualTo(sessionsBeforeRoleRemoval);
        assertProblem(send("GET", USERS, customerStaleAdminJwt, null), 403, "ADMIN_ACCESS_DENIED");
        HttpResponse<String> currentRoleRefresh = send("POST", "/api/v1/auth/refresh", null,
                "{\"refresh_token\":\"" + refreshAfterRoleIssue + "\"}");
        assertThat(currentRoleRefresh.statusCode()).isEqualTo(200);
        assertThat(jwtRoles(json(currentRoleRefresh).path("access_token").asText())).containsExactly("CUSTOMER");

        HttpResponse<String> demotedB = send("PUT", USERS + "/" + adminB.id() + "/roles",
                adminAToken, "{\"roles\":[\"CUSTOMER\"]}");
        assertThat(demotedB.statusCode()).isEqualTo(200);
        assertProblem(send("GET", USERS, adminBToken, null), 403, "ADMIN_ACCESS_DENIED");
        assertThat(qualifyingAdminCount()).isEqualTo(1);
        String customerSessionsBeforeAdminChanges = sessionState(customer.id());

        assertProblem(send("PATCH", USERS + "/" + adminA.id() + "/status", adminAToken,
                "{\"account_status\":\"SUSPENDED\"}"), 409, "LAST_QUALIFYING_ADMIN_REQUIRED");
        assertProblem(send("PUT", USERS + "/" + adminA.id() + "/roles", adminAToken,
                "{\"roles\":[\"CUSTOMER\"]}"), 409, "LAST_QUALIFYING_ADMIN_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT account_status FROM public.account WHERE account_id = ?",
                String.class, adminA.id())).isEqualTo("ACTIVE");
        assertThat(roles(adminA.id())).containsExactly("ADMIN", "CUSTOMER");
        assertThat(sessionState(customer.id())).isEqualTo(customerSessionsBeforeAdminChanges);
    }

    @Test
    void failuresAfterRoleDeleteAndDuringStatusUpdateRollBackWithoutSessionEffects() throws Exception {
        User admin = account("ACTIVE", true, false, "ADMIN");
        User customer = account("ACTIVE", true, false, "CUSTOMER");
        jdbc.update("""
                INSERT INTO public.refresh_session(session_id, account_id, token_hash, issued_at, expires_at)
                VALUES (?, ?, ?, now(), now() + interval '7 days')
                """, UUID.randomUUID(), customer.id(), UUID.randomUUID().toString().replace("-", ""));
        String beforeAccount = accountState(customer.id());
        String beforeSession = sessionState(customer.id());

        FailureTrigger roleFailure = installRoleInsertFailure(customer.id());
        HttpResponse<String> failedRoles;
        try {
            failedRoles = send("PUT", USERS + "/" + customer.id() + "/roles",
                    token(admin.id(), "ADMIN"), "{\"roles\":[\"ADMIN\",\"CUSTOMER\"]}");
        } finally {
            removeFailureTrigger("account_role", roleFailure);
        }
        assertProblem(failedRoles, 500, "INTERNAL_ERROR");
        assertThat(roles(customer.id())).containsExactly("CUSTOMER");
        assertThat(accountState(customer.id())).isEqualTo(beforeAccount);
        assertThat(sessionState(customer.id())).isEqualTo(beforeSession);

        FailureTrigger statusFailure = installStatusUpdateFailure(customer.id());
        HttpResponse<String> failedStatus;
        try {
            failedStatus = send("PATCH", USERS + "/" + customer.id() + "/status",
                    token(admin.id(), "ADMIN"), "{\"account_status\":\"SUSPENDED\"}");
        } finally {
            removeFailureTrigger("account", statusFailure);
        }
        assertProblem(failedStatus, 500, "INTERNAL_ERROR");
        assertThat(jdbc.queryForObject("SELECT account_status FROM public.account WHERE account_id = ?",
                String.class, customer.id())).isEqualTo("ACTIVE");
        assertThat(accountState(customer.id())).isEqualTo(beforeAccount);
        assertThat(sessionState(customer.id())).isEqualTo(beforeSession);
    }

    @Test
    void concurrentAdminRoleRemovalSerializesAndKeepsAQualifyingAdmin() throws Exception {
        User adminA = account("ACTIVE", true, false, "ADMIN");
        User adminB = account("ACTIVE", true, false, "ADMIN");
        PairResult pair = runContendedPair(
                adminA, adminB, "PUT", USERS + "/" + adminB.id() + "/roles", "{\"roles\":[\"CUSTOMER\"]}",
                "PUT", USERS + "/" + adminA.id() + "/roles", "{\"roles\":[\"CUSTOMER\"]}");
        assertOnlyFirstMayCommit(pair);
        assertThat(qualifyingAdminCount()).isEqualTo(1);
        assertThat(List.of(roles(adminA.id()), roles(adminB.id())))
                .contains(List.of("ADMIN"), List.of("CUSTOMER"));
    }

    @Test
    void concurrentAdminSuspensionsSerializeAndKeepAQualifyingAdmin() throws Exception {
        User adminA = account("ACTIVE", true, false, "ADMIN");
        User adminB = account("ACTIVE", true, false, "ADMIN");
        PairResult pair = runContendedPair(
                adminA, adminB, "PATCH", USERS + "/" + adminB.id() + "/status", "{\"account_status\":\"SUSPENDED\"}",
                "PATCH", USERS + "/" + adminA.id() + "/status", "{\"account_status\":\"SUSPENDED\"}");
        assertOnlyFirstMayCommit(pair);
        assertThat(qualifyingAdminCount()).isEqualTo(1);
        assertThat(List.of(
                jdbc.queryForObject("SELECT account_status FROM public.account WHERE account_id = ?",
                        String.class, adminA.id()),
                jdbc.queryForObject("SELECT account_status FROM public.account WHERE account_id = ?",
                        String.class, adminB.id()))).contains("ACTIVE", "SUSPENDED");
    }

    @Test
    void concurrentSuspendAndRoleRemovalSerializeAndKeepAQualifyingAdmin() throws Exception {
        User adminA = account("ACTIVE", true, false, "ADMIN");
        User adminB = account("ACTIVE", true, false, "ADMIN");
        PairResult pair = runContendedPair(
                adminA, adminB, "PATCH", USERS + "/" + adminB.id() + "/status", "{\"account_status\":\"SUSPENDED\"}",
                "PUT", USERS + "/" + adminA.id() + "/roles", "{\"roles\":[\"CUSTOMER\"]}");
        assertOnlyFirstMayCommit(pair);
        assertThat(qualifyingAdminCount()).isEqualTo(1);
        assertThat(isQualifyingAdmin(adminA.id()) ^ isQualifyingAdmin(adminB.id())).isTrue();
    }

    @Test
    void roleRemovalThatCommitsBeforeListAuthorizationMakesOldAdminJwtForbidden() throws Exception {
        User adminA = account("ACTIVE", true, false, "ADMIN", "CUSTOMER");
        User adminB = account("ACTIVE", true, false, "ADMIN", "CUSTOMER");
        String tokenA = token(adminA.id(), "ADMIN");
        String tokenB = token(adminB.id(), "ADMIN");
        CountDownLatch roleReplacementWritten = new CountDownLatch(1);
        CountDownLatch releaseRoleReplacement = new CountDownLatch(1);
        doAnswer(invocation -> {
            int inserted = (int) invocation.callRealMethod();
            roleReplacementWritten.countDown();
            if (!releaseRoleReplacement.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test synchronization timed out");
            }
            return inserted;
        }).when(repository).insertRole(adminA.id(), "CUSTOMER");

        CompletableFuture<HttpResponse<String>> write = sendAsync(
                "PUT", USERS + "/" + adminA.id() + "/roles", tokenB, "{\"roles\":[\"CUSTOMER\"]}");
        CompletableFuture<HttpResponse<String>> list = null;
        try {
            assertThat(roleReplacementWritten.await(15, TimeUnit.SECONDS)).isTrue();
            clearInvocations(repository);
            list = sendAsync("GET", USERS, tokenA, null);
            awaitDatabaseLockWait("pg_advisory_xact_lock_shared(");
            releaseRoleReplacement.countDown();

            HttpResponse<String> writeResponse = write.get(25, TimeUnit.SECONDS);
            HttpResponse<String> listResponse = list.get(25, TimeUnit.SECONDS);
            assertThat(writeResponse.statusCode()).isEqualTo(200);
            assertNoStore(writeResponse);
            assertThat(listResponse.statusCode())
                    .as("a list request waiting behind committed ADMIN removal must revalidate the actor")
                    .isEqualTo(403);
            assertProblem(listResponse, 403, "ADMIN_ACCESS_DENIED");
            assertThat(json(listResponse).path("correlationId").asText()).isNotBlank();
            assertThat(listResponse.headers().firstValue("X-Correlation-Id").orElse(""))
                    .isEqualTo(json(listResponse).path("correlationId").asText());
            assertThat(listResponse.body()).doesNotContain(adminA.email(), adminB.email());
            verify(repository, never()).userPage(any());
            assertThat(roles(adminA.id())).containsExactly("CUSTOMER");
            assertThat(qualifyingAdminCount()).isEqualTo(1);

            clearInvocations(repository);
            HttpResponse<String> head = send("HEAD", USERS, tokenA, null);
            assertThat(head.statusCode()).isEqualTo(403);
            assertThat(head.body()).isEmpty();
            assertNoStore(head);
            verify(repository, never()).userPage(any());
        } finally {
            releaseRoleReplacement.countDown();
            write.get(25, TimeUnit.SECONDS);
            if (list != null) {
                list.get(25, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void suspensionThatCommitsBeforeListAuthorizationReturnsForbiddenWithoutLeakingData() throws Exception {
        User adminA = account("ACTIVE", true, false, "ADMIN", "CUSTOMER");
        User adminB = account("ACTIVE", true, false, "ADMIN", "CUSTOMER");
        String tokenA = token(adminA.id(), "ADMIN");
        String tokenB = token(adminB.id(), "ADMIN");
        CountDownLatch suspensionWritten = new CountDownLatch(1);
        CountDownLatch releaseSuspension = new CountDownLatch(1);
        doAnswer(invocation -> {
            int updated = (int) invocation.callRealMethod();
            suspensionWritten.countDown();
            if (!releaseSuspension.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test synchronization timed out");
            }
            return updated;
        }).when(repository).updateStatus(adminA.id(), "SUSPENDED");

        CompletableFuture<HttpResponse<String>> write = sendAsync(
                "PATCH", USERS + "/" + adminA.id() + "/status", tokenB,
                "{\"account_status\":\"SUSPENDED\"}");
        CompletableFuture<HttpResponse<String>> list = null;
        try {
            assertThat(suspensionWritten.await(15, TimeUnit.SECONDS)).isTrue();
            clearInvocations(repository);
            list = sendAsync("GET", USERS, tokenA, null);
            awaitDatabaseLockWait("pg_advisory_xact_lock_shared(");
            releaseSuspension.countDown();

            HttpResponse<String> writeResponse = write.get(25, TimeUnit.SECONDS);
            HttpResponse<String> listResponse = list.get(25, TimeUnit.SECONDS);
            assertThat(writeResponse.statusCode()).isEqualTo(200);
            assertNoStore(writeResponse);
            assertThat(listResponse.statusCode())
                    .as("a list request waiting behind committed suspension must return the live authorization result")
                    .isEqualTo(403);
            assertProblem(listResponse, 403, "ADMIN_ACCESS_DENIED");
            assertThat(json(listResponse).path("correlationId").asText()).isNotBlank();
            assertThat(listResponse.headers().firstValue("X-Correlation-Id").orElse(""))
                    .isEqualTo(json(listResponse).path("correlationId").asText());
            assertThat(listResponse.body()).doesNotContain(adminA.email(), adminB.email());
            verify(repository, never()).userPage(any());
            assertThat(jdbc.queryForObject("SELECT account_status FROM public.account WHERE account_id = ?",
                    String.class, adminA.id())).isEqualTo("SUSPENDED");
            assertThat(roles(adminA.id())).containsExactly("ADMIN", "CUSTOMER");
            assertThat(qualifyingAdminCount()).isEqualTo(1);
        } finally {
            releaseSuspension.countDown();
            write.get(25, TimeUnit.SECONDS);
            if (list != null) {
                list.get(25, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void listAuthorizationWinsBeforeAdminWriteAndStaleJwtIsDeniedOnNextRequest() throws Exception {
        User adminA = account("ACTIVE", true, false, "ADMIN", "CUSTOMER");
        User adminB = account("ACTIVE", true, false, "ADMIN", "CUSTOMER");
        String tokenA = token(adminA.id(), "ADMIN");
        String tokenB = token(adminB.id(), "ADMIN");
        CountDownLatch listAuthorizedBeforeCount = new CountDownLatch(1);
        CountDownLatch releaseList = new CountDownLatch(1);
            doAnswer(invocation -> {
            listAuthorizedBeforeCount.countDown();
            if (!releaseList.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test synchronization timed out");
            }
            return invocation.callRealMethod();
        }).when(repository).userPage(any());

        CompletableFuture<HttpResponse<String>> list = sendAsync("GET", USERS, tokenA, null);
        CompletableFuture<HttpResponse<String>> write = null;
        try {
            assertThat(listAuthorizedBeforeCount.await(15, TimeUnit.SECONDS)).isTrue();
            write = sendAsync("PUT", USERS + "/" + adminA.id() + "/roles", tokenB,
                    "{\"roles\":[\"CUSTOMER\"]}");
            awaitDatabaseLockWait("pg_advisory_xact_lock(");
            releaseList.countDown();

            HttpResponse<String> listResponse = list.get(25, TimeUnit.SECONDS);
            HttpResponse<String> writeResponse = write.get(25, TimeUnit.SECONDS);
            assertThat(listResponse.statusCode()).isEqualTo(200);
            assertNoStore(listResponse);
            assertThat(strings(findItem(json(listResponse), adminA.id()).path("roles")))
                    .containsExactly("ADMIN", "CUSTOMER");
            assertThat(writeResponse.statusCode()).isEqualTo(200);
            assertNoStore(writeResponse);
            assertThat(roles(adminA.id())).containsExactly("CUSTOMER");
            assertThat(qualifyingAdminCount()).isEqualTo(1);

            clearInvocations(repository);
            HttpResponse<String> staleList = send("GET", USERS, tokenA, null);
            assertProblem(staleList, 403, "ADMIN_ACCESS_DENIED");
            assertThat(staleList.body()).doesNotContain(adminA.email(), adminB.email());
            verify(repository, never()).userPage(any());

            clearInvocations(repository);
            HttpResponse<String> staleHead = send("HEAD", USERS, tokenA, null);
            assertThat(staleHead.statusCode()).isEqualTo(403);
            assertThat(staleHead.body()).isEmpty();
            assertNoStore(staleHead);
            verify(repository, never()).userPage(any());
        } finally {
            releaseList.countDown();
            list.get(25, TimeUnit.SECONDS);
            if (write != null) {
                write.get(25, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void rolledBackAdminSuspensionLeavesActorEligibleToList() throws Exception {
        User adminA = account("ACTIVE", true, false, "ADMIN", "CUSTOMER");
        User adminB = account("ACTIVE", true, false, "ADMIN", "CUSTOMER");
        String tokenA = token(adminA.id(), "ADMIN");
        String tokenB = token(adminB.id(), "ADMIN");
        String before = accountState(adminA.id());
        FailureTrigger failure = installStatusUpdateFailure(adminA.id());
        try {
            HttpResponse<String> failedWrite = send("PATCH", USERS + "/" + adminA.id() + "/status", tokenB,
                    "{\"account_status\":\"SUSPENDED\"}");
            assertProblem(failedWrite, 500, "INTERNAL_ERROR");
            assertThat(failedWrite.body()).doesNotContain(adminA.email(), adminB.email());
            assertThat(json(failedWrite).path("correlationId").asText()).isNotBlank();
            assertThat(accountState(adminA.id())).isEqualTo(before);
            assertThat(roles(adminA.id())).containsExactly("ADMIN", "CUSTOMER");

            clearInvocations(repository);
            HttpResponse<String> list = send("GET", USERS, tokenA, null);
            assertThat(list.statusCode()).isEqualTo(200);
            assertNoStore(list);
            assertThat(strings(findItem(json(list), adminA.id()).path("roles")))
                    .containsExactly("ADMIN", "CUSTOMER");

            clearInvocations(repository);
            HttpResponse<String> head = send("HEAD", USERS, tokenA, null);
            assertThat(head.statusCode()).isEqualTo(200);
            assertThat(head.body()).isEmpty();
            assertNoStore(head);
        } finally {
            removeFailureTrigger("account", failure);
        }
    }

    private PairResult runContendedPair(User firstActor, User secondActor,
            String firstMethod, String firstPath, String firstBody,
            String secondMethod, String secondPath, String secondBody) throws Exception {
        reset(repository);
        CountDownLatch firstOwnsDatabaseLock = new CountDownLatch(1);
        CountDownLatch secondAttemptsDatabaseLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                Object result = invocation.callRealMethod();
                firstOwnsDatabaseLock.countDown();
                if (!releaseFirst.await(20, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test synchronization timed out");
                }
                return result;
            }
            if (call == 2) {
                secondAttemptsDatabaseLock.countDown();
            }
            return invocation.callRealMethod();
        }).when(repository).lockAdminMutations();

        CompletableFuture<HttpResponse<String>> first = sendAsync(firstMethod, firstPath,
                token(firstActor.id(), "ADMIN"), firstBody);
        if (!firstOwnsDatabaseLock.await(15, TimeUnit.SECONDS)) {
            releaseFirst.countDown();
            throw new AssertionError("First administrative request did not acquire the advisory lock");
        }
        CompletableFuture<HttpResponse<String>> second = sendAsync(secondMethod, secondPath,
                token(secondActor.id(), "ADMIN"), secondBody);
        try {
            assertThat(secondAttemptsDatabaseLock.await(15, TimeUnit.SECONDS)).isTrue();
            awaitAdvisoryLockWait();
        } finally {
            releaseFirst.countDown();
        }
        HttpResponse<String> firstResponse = first.get(20, TimeUnit.SECONDS);
        HttpResponse<String> secondResponse = second.get(20, TimeUnit.SECONDS);
        return new PairResult(firstResponse, secondResponse);
    }

    private void assertOnlyFirstMayCommit(PairResult pair) throws Exception {
        assertThat(pair.first().statusCode()).isEqualTo(200);
        assertNoStore(pair.first());
        assertProblem(pair.second(), 403, "ADMIN_ACCESS_DENIED");
    }

    private void awaitAdvisoryLockWait() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity
                    WHERE datname = current_database() AND state = 'active'
                      AND wait_event_type = 'Lock'
                      AND query ILIKE '%pg_advisory_xact_lock%'
                    """, Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(15);
        }
        throw new AssertionError("Second administrative request did not wait on PostgreSQL advisory lock");
    }

    private void awaitDatabaseLockWait(String queryFragment) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity
                    WHERE datname = current_database()
                      AND state = 'active'
                      AND wait_event_type = 'Lock'
                      AND query ILIKE ?
                      AND cardinality(pg_blocking_pids(pid)) > 0
                    """, Integer.class, "%" + queryFragment + "%");
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(15);
        }
        throw new AssertionError("No PostgreSQL lock wait observed for query containing " + queryFragment);
    }

    private User account(String status, boolean verified, boolean usablePassword, String... roles) {
        String email = "manage-users-" + UUID.randomUUID() + "@example.test";
        return accountWithEmail(status, verified, usablePassword, email, roles);
    }

    private User accountWithEmail(String status, boolean verified, boolean usablePassword,
            String email, String... roles) {
        UUID id = UUID.randomUUID();
        String passwordHash = usablePassword ? passwordEncoder.encode(TEST_PASSWORD) : "test-only-not-a-password-hash";
        OffsetDateTime registeredAt = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime verifiedAt = verified ? registeredAt.minusDays(1) : null;
        jdbc.update("""
                INSERT INTO public.account (
                    account_id, email, password_hash, account_status, email_verified_at, registered_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, id, email, passwordHash, status, verifiedAt, registeredAt);
        for (String role : roles) {
            jdbc.update("INSERT INTO public.account_role(account_id, role_code) VALUES (?, ?)", id, role);
        }
        return new User(id, email);
    }

    private HttpResponse<String> login(User user) throws Exception {
        return send("POST", "/api/v1/auth/login", null,
                "{\"email\":\"" + user.email() + "\",\"password\":\"" + TEST_PASSWORD + "\"}");
    }

    private HttpResponse<String> send(String method, String path, String bearer, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(25));
        if (bearer != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
        }
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private CompletableFuture<HttpResponse<String>> sendAsync(
            String method, String path, String bearer, String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(30));
        if (bearer != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
        }
        if (body != null) {
            request.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        }
        HttpRequest requestValue = request.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build();
        return HttpClient.newHttpClient().sendAsync(requestValue, HttpResponse.BodyHandlers.ofString());
    }

    private String token(UUID subject, String role) throws Exception {
        return token(subject, List.of(role), Instant.now().plusSeconds(900),
                JwtTestSupport.ISSUER, JwtTestSupport.AUDIENCE);
    }

    private String token(UUID subject, List<String> roles, Instant expiration, String issuer, String audience)
            throws Exception {
        Instant now = Instant.now();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(JwtTestSupport.SIGNING_KEY.getKeyID()).build(),
                new JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject(subject.toString())
                        .jwtID(UUID.randomUUID().toString()).issueTime(Date.from(now.minusSeconds(1)))
                        .expirationTime(Date.from(expiration)).claim("roles", roles).build());
        jwt.sign(new RSASSASigner(JwtTestSupport.SIGNING_KEY.toPrivateKey()));
        return jwt.serialize();
    }

    private String tamper(String jwt) {
        String[] parts = jwt.split("\\.");
        char replacement = parts[2].charAt(0) == 'A' ? 'B' : 'A';
        parts[2] = replacement + parts[2].substring(1);
        return String.join(".", parts);
    }

    private List<String> jwtRoles(String jwt) throws Exception {
        SignedJWT parsed = SignedJWT.parse(jwt);
        return parsed.getJWTClaimsSet().getStringListClaim("roles");
    }

    private JsonNode json(HttpResponse<String> response) throws Exception {
        return mapper.readTree(response.body());
    }

    private JsonNode findItem(JsonNode page, UUID id) {
        for (JsonNode item : page.path("items")) {
            if (id.toString().equals(item.path("account_id").asText())) {
                return item;
            }
        }
        throw new AssertionError("Expected user was not present in the page");
    }

    private List<String> strings(JsonNode array) {
        List<String> result = new ArrayList<>();
        array.forEach(value -> result.add(value.asText()));
        return result;
    }

    private List<String> roles(UUID id) {
        return jdbc.query("SELECT role_code FROM public.account_role WHERE account_id = ? ORDER BY role_code",
                (rs, row) -> rs.getString(1), id);
    }

    private OffsetDateTime verifiedAt(UUID id) {
        return jdbc.queryForObject("SELECT email_verified_at FROM public.account WHERE account_id = ?",
                OffsetDateTime.class, id);
    }

    private String passwordHash(UUID id) {
        return jdbc.queryForObject("SELECT password_hash FROM public.account WHERE account_id = ?",
                String.class, id);
    }

    private int sessionCount(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM public.refresh_session WHERE account_id = ?",
                Integer.class, id);
    }

    private int activeSessionCount(UUID id) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM public.refresh_session
                WHERE account_id = ? AND revoked_at IS NULL
                """, Integer.class, id);
    }

    private long qualifyingAdminCount() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM public.account a
                WHERE a.account_status = 'ACTIVE' AND a.email_verified_at IS NOT NULL
                  AND EXISTS (SELECT 1 FROM public.account_role r
                              WHERE r.account_id = a.account_id AND r.role_code = 'ADMIN')
                """, Long.class);
    }

    private boolean isQualifyingAdmin(UUID id) {
        return jdbc.queryForObject("""
                SELECT a.account_status = 'ACTIVE' AND a.email_verified_at IS NOT NULL
                   AND EXISTS (SELECT 1 FROM public.account_role r
                               WHERE r.account_id = a.account_id AND r.role_code = 'ADMIN')
                FROM public.account a WHERE a.account_id = ?
                """, Boolean.class, id);
    }

    private FailureTrigger installRoleInsertFailure(UUID accountId) {
        return installFailureTrigger("account_role", accountId, "NEW.role_code = 'ADMIN'");
    }

    private FailureTrigger installStatusUpdateFailure(UUID accountId) {
        return installFailureTrigger("account", accountId,
                "NEW.account_status = 'SUSPENDED' AND TG_OP = 'UPDATE'");
    }

    private FailureTrigger installFailureTrigger(String table, UUID accountId, String failureCondition) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String trigger = "test_failure_" + suffix;
        String function = "test_failure_fn_" + suffix;
        jdbc.execute(String.format("""
                CREATE FUNCTION public.%s() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.account_id = '%s'::uuid AND (%s) THEN
                        RAISE EXCEPTION 'injected integration-test write failure';
                    END IF;
                    RETURN NEW;
                END
                $$
                """, function, accountId, failureCondition));
        jdbc.execute(String.format("""
                CREATE TRIGGER %s BEFORE INSERT OR UPDATE ON public.%s
                FOR EACH ROW EXECUTE FUNCTION public.%s()
                """, trigger, table, function));
        return new FailureTrigger(trigger, function);
    }

    private void removeFailureTrigger(String table, FailureTrigger failureTrigger) {
        jdbc.execute(String.format("DROP TRIGGER %s ON public.%s", failureTrigger.trigger(), table));
        jdbc.execute(String.format("DROP FUNCTION public.%s()", failureTrigger.function()));
    }

    private String accountState(UUID id) {
        return jdbc.queryForObject("""
                SELECT email || '|' || password_hash || '|' || account_status || '|'
                    || COALESCE(email_verified_at::text, 'NULL') || '|' || registered_at::text
                FROM public.account WHERE account_id = ?
                """, String.class, id);
    }

    private String sessionState(UUID id) {
        return jdbc.queryForObject("""
                SELECT COALESCE(string_agg(token_hash || ':' || COALESCE(revoked_at::text, 'NULL'), ','), '')
                FROM public.refresh_session WHERE account_id = ?
                """, String.class, id);
    }

    private void assertProblem(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        JsonNode problem = json(response);
        assertThat(problem.path("code").asText()).isEqualTo(code);
        assertThat(response.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse(""))
                .contains("application/problem+json");
        assertNoStore(response);
    }

    private void assertNoStore(HttpResponse<String> response) {
        assertThat(response.headers().firstValue(HttpHeaders.CACHE_CONTROL).orElse(""))
                .contains("no-store");
    }

    private String refreshRow(String tokenHash, User user) {
        return tokenHash + ":" + user.id();
    }

    private record User(UUID id, String email) {
    }

    private record Route(String method, String path, String body) {
    }

    private record PairResult(HttpResponse<String> first, HttpResponse<String> second) {
    }

    private record FailureTrigger(String trigger, String function) {
    }
}
