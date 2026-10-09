package com.stylemind.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.reset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.stylemind.identity.repository.AddressRepository;
import com.stylemind.identity.service.ManageAddressService;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.health.db.enabled=false")
@Testcontainers
class ManageAddressPostgresIntegrationTests {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6")
            .withDatabaseName("stylemind_identity_address_test")
            .withUsername("identity_address_test")
            .withPassword("identity_address_test_only");
    private static final String HMAC_SECRET = JwtTestSupport.randomSecret();
    private static final String SERVICE_TOKEN = JwtTestSupport.randomSecret();
    private static final String ADDRESSES = "/api/v1/me/addresses";

    @Container private static final PostgreSQLContainer<?> POSTGRES_CONTAINER = POSTGRES;

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
    @LocalServerPort private int port;
    @MockitoSpyBean private AddressRepository repository;
    @MockitoSpyBean private ManageAddressService addressService;

    @BeforeEach
    void clearDisposableAddressRows() {
        reset(repository);
        reset(addressService);
        jdbc.update("DELETE FROM public.customer_address");
    }

    @Test
    void realHttpEnforcesSelfOwnershipAndRunsAddressLifecycle() throws Exception {
        UUID owner = account("CUSTOMER");
        UUID other = account("CUSTOMER");
        UUID admin = account("ADMIN");
        var accountBefore = accountRow(owner);
        var rolesBefore = roles(owner);
        int unrelatedRowsBefore = unrelatedRows(owner);
        assertThat(json(send("GET", ADDRESSES, token(owner, "CUSTOMER"), null)).path("addresses")).isEmpty();
        assertThat(send("GET", ADDRESSES, null, null).statusCode()).isEqualTo(401);
        assertThat(send("GET", ADDRESSES, token(owner, "STYLIST"), null).statusCode()).isEqualTo(403);
        assertThat(send("GET", ADDRESSES, token(owner, "CUSTOMER"), null, "?account_id=" + other)
                .statusCode()).isEqualTo(400);
        assertThat(send("GET", ADDRESSES, token(UUID.randomUUID(), "CUSTOMER"), null).statusCode()).isEqualTo(404);
        assertThat(send("GET", ADDRESSES, token(admin, "ADMIN"), null).statusCode()).isEqualTo(200);

        HttpResponse<String> createOne = send("POST", ADDRESSES, token(owner, "CUSTOMER"),
                body("Nhà", "Nguyễn An", "0912 345 678", "12 Đường A", "00004"));
        assertThat(createOne.statusCode()).isEqualTo(201);
        assertThat(createOne.headers().firstValue("Cache-Control")).contains("no-store");
        UUID first = UUID.fromString(json(createOne).path("address_id").asText());
        assertThat(send("GET", ADDRESSES + "/" + first, token(admin, "ADMIN"), null).statusCode()).isEqualTo(404);
        assertThat(createOne.headers().firstValue("Location")).contains(ADDRESSES + "/" + first);
        assertThat(json(createOne).path("is_default").asBoolean()).isTrue();
        assertThat(json(createOne).path("recipient_phone").asText()).isEqualTo("+84912345678");
        assertThat(createOne.body()).doesNotContain("account_id", "password_hash", "role_code", "token_hash");

        HttpResponse<String> createTwo = send("POST", ADDRESSES, token(owner, "CUSTOMER"),
                body(null, "Trần Bình", "+84 (912) 345-678", "25 Ngõ B", null));
        assertThat(createTwo.statusCode()).isEqualTo(201);
        UUID second = UUID.fromString(json(createTwo).path("address_id").asText());
        assertThat(json(createTwo).path("is_default").asBoolean()).isFalse();
        JsonNode list = json(send("GET", ADDRESSES, token(owner, "CUSTOMER"), null)).path("addresses");
        assertThat(list).hasSize(2);
        assertThat(list.get(0).path("address_id").asText()).isEqualTo(first.toString());

        assertThat(send("GET", ADDRESSES + "/" + first, token(other, "CUSTOMER"), null).statusCode()).isEqualTo(404);
        assertThat(send("PATCH", ADDRESSES + "/" + first, token(other, "CUSTOMER"), "{\"label\":\"Hacked\"}")
                .statusCode()).isEqualTo(404);
        assertThat(send("PUT", ADDRESSES + "/" + first + "/default", token(other, "CUSTOMER"), null).statusCode())
                .isEqualTo(404);
        assertThat(send("DELETE", ADDRESSES + "/" + first, token(other, "CUSTOMER"), null).statusCode()).isEqualTo(404);
        assertThat(send("GET", ADDRESSES + "/not-a-uuid", token(owner, "CUSTOMER"), null).statusCode()).isEqualTo(400);

        HttpResponse<String> patch = send("PATCH", ADDRESSES + "/" + second, token(owner, "CUSTOMER"),
                "{\"label\":null,\"address_line\":\"  Căn 2  \",\"ward_code\":null}");
        assertThat(patch.statusCode()).isEqualTo(200);
        assertThat(json(patch).path("label").isNull()).isTrue();
        assertThat(json(patch).path("ward_code").isNull()).isTrue();
        assertThat(json(patch).path("address_line").asText()).isEqualTo("Căn 2");
        assertThat(json(patch).path("is_default").asBoolean()).isFalse();
        assertThat(send("PATCH", ADDRESSES + "/" + second, token(owner, "CUSTOMER"),
                "{\"account_id\":\"" + other + "\"}").statusCode()).isEqualTo(400);

        assertThat(send("PUT", ADDRESSES + "/" + second + "/default", token(owner, "CUSTOMER"), null).statusCode())
                .isEqualTo(200);
        assertThat(send("PUT", ADDRESSES + "/" + second + "/default", token(owner, "CUSTOMER"), null).statusCode())
                .isEqualTo(200);
        HttpResponse<String> cannotDeleteDefault = send("DELETE", ADDRESSES + "/" + second,
                token(owner, "CUSTOMER"), null);
        assertThat(cannotDeleteDefault.statusCode()).isEqualTo(409);
        assertThat(json(cannotDeleteDefault).path("code").asText()).isEqualTo("DEFAULT_ADDRESS_CHANGE_REQUIRED");
        assertThat(count(owner)).isEqualTo(2);
        assertThat(defaultCount(owner)).isEqualTo(1);
        assertThat(send("DELETE", ADDRESSES + "/" + first, token(owner, "CUSTOMER"), null).statusCode()).isEqualTo(204);
        assertThat(send("DELETE", ADDRESSES + "/" + second, token(owner, "CUSTOMER"), null).statusCode()).isEqualTo(204);
        assertThat(json(send("GET", ADDRESSES, token(owner, "CUSTOMER"), null)).path("addresses")).isEmpty();
        assertThat(send("GET", ADDRESSES + "/" + first, token(owner, "CUSTOMER"), null).statusCode()).isEqualTo(404);
        assertThat(accountRow(owner)).isEqualTo(accountBefore);
        assertThat(roles(owner)).isEqualTo(rolesBefore);
        assertThat(unrelatedRows(owner)).isEqualTo(unrelatedRowsBefore);
    }

    @Test
    void allEightApiRoutesRequireJwtAndCustomerOrAdminAuthority() throws Exception {
        UUID allowedSubject = account("CUSTOMER");
        UUID disallowedSubject = account("CUSTOMER");
        String validBody = body(null, "A", "0912345678", "Address", null);
        List<String[]> routes = List.of(
                new String[] {"GET", ADDRESSES, null},
                new String[] {"GET", ADDRESSES + "/" + UUID.randomUUID(), null},
                new String[] {"POST", ADDRESSES, validBody},
                new String[] {"PATCH", ADDRESSES + "/" + UUID.randomUUID(), "{\"label\":\"A\"}"},
                new String[] {"PUT", ADDRESSES + "/" + UUID.randomUUID() + "/default", null},
                new String[] {"DELETE", ADDRESSES + "/" + UUID.randomUUID(), null},
                new String[] {"GET", "/api/v1/locations/provinces", null},
                new String[] {"GET", "/api/v1/locations/provinces/01/wards", null});
        for (String[] route : routes) {
            assertThat(send(route[0], route[1], null, route[2]).statusCode())
                    .as("missing JWT for %s %s", route[0], route[1]).isEqualTo(401);
            assertThat(send(route[0], route[1], token(disallowedSubject, "STYLIST"), route[2]).statusCode())
                    .as("disallowed role for %s %s", route[0], route[1]).isEqualTo(403);
        }
        assertThat(count(allowedSubject)).isZero();
        assertThat(count(disallowedSubject)).isZero();
    }

    @Test
    void headUsesTheSameRoleAndAuthenticationGateAsGet() throws Exception {
        UUID owner = account("CUSTOMER");
        UUID other = account("CUSTOMER");
        UUID addressId = rawAddress(owner, true, "Head");
        String customer = token(owner, "CUSTOMER");
        String admin = token(owner, "ADMIN");
        String otherCustomer = token(other, "CUSTOMER");
        String unsupportedRole = token(owner, "STYLIST");
        String valid = token(owner, "CUSTOMER");
        String[] parts = valid.split("\\.");
        parts[2] = (parts[2].charAt(0) == 'A' ? "B" : "A") + parts[2].substring(1);
        String badSignature = String.join(".", parts);
        String expired = token(owner, "CUSTOMER", JwtTestSupport.ISSUER, JwtTestSupport.AUDIENCE,
                Instant.now().minusSeconds(1));
        String ownerStateBefore = state(owner);
        List<String> paths = List.of(ADDRESSES, ADDRESSES + "/" + addressId,
                "/api/v1/locations/provinces", "/api/v1/locations/provinces/01/wards");

        clearInvocations(addressService);
        AtomicInteger unsupportedRoleHandlerCalls = new AtomicInteger();
        doAnswer(invocation -> { unsupportedRoleHandlerCalls.incrementAndGet(); return invocation.callRealMethod(); })
                .when(addressService).list(org.mockito.ArgumentMatchers.any());
        doAnswer(invocation -> { unsupportedRoleHandlerCalls.incrementAndGet(); return invocation.callRealMethod(); })
                .when(addressService).get(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        doAnswer(invocation -> { unsupportedRoleHandlerCalls.incrementAndGet(); return invocation.callRealMethod(); })
                .when(addressService).provinces();
        doAnswer(invocation -> { unsupportedRoleHandlerCalls.incrementAndGet(); return invocation.callRealMethod(); })
                .when(addressService).wards(org.mockito.ArgumentMatchers.any());
        List<String> unsupportedRoleResults = new java.util.ArrayList<>();
        for (String path : paths) {
            assertThat(sendHead(path, null).statusCode()).as("missing JWT HEAD %s", path).isEqualTo(401);
            assertThat(sendHead(path, "not-a-jwt").statusCode()).as("malformed JWT HEAD %s", path).isEqualTo(401);
            assertThat(sendHead(path, badSignature).statusCode()).as("bad signature HEAD %s", path).isEqualTo(401);
            assertThat(sendHead(path, expired).statusCode()).as("expired JWT HEAD %s", path).isEqualTo(401);
            unsupportedRoleResults.add(path + "=" + sendHead(path, unsupportedRole).statusCode());
        }
        assertThat(unsupportedRoleResults).as("unsupported-role HEAD outcomes; Address handler calls="
                + unsupportedRoleHandlerCalls.get()).containsOnly(paths.stream().map(path -> path + "=403").toArray(String[]::new));
        assertThat(unsupportedRoleHandlerCalls).as("unsupported role must not reach a business handler").hasValue(0);
        verifyNoInteractions(addressService);

        for (String path : paths) {
            assertThat(sendHead(path, customer).statusCode()).as("CUSTOMER HEAD %s", path).isEqualTo(200);
            assertThat(sendHead(path, admin).statusCode()).as("ADMIN HEAD %s", path).isEqualTo(200);
        }
        assertThat(sendHead(ADDRESSES + "/" + addressId, otherCustomer).statusCode())
                .as("CUSTOMER HEAD respects resource ownership").isEqualTo(404);
        assertThat(send("GET", ADDRESSES + "/" + addressId, otherCustomer, null).statusCode())
                .as("HEAD ownership outcome matches GET").isEqualTo(404);
        assertThat(state(owner)).as("HEAD requests do not mutate Address rows").isEqualTo(ownerStateBefore);
        assertThat(count(owner)).isEqualTo(1);
        assertThat(defaultCount(owner)).isEqualTo(1);
    }

    @Test
    void unsupportedMethodAndOptionsKeepFrameworkAndCorsBehavior() throws Exception {
        UUID owner = account("CUSTOMER");
        String customer = token(owner, "CUSTOMER");
        clearInvocations(addressService);

        HttpResponse<String> unsupported = send("PATCH", "/api/v1/locations/provinces", customer, "{}");
        assertThat(unsupported.statusCode()).isEqualTo(405);
        assertThat(send("PATCH", "/api/v1/locations/provinces", null, "{}").statusCode()).isEqualTo(401);

        HttpRequest optionsRequest = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                        + "/api/v1/locations/provinces"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + customer)
                .header("Origin", "https://postman.example.test")
                .header("Access-Control-Request-Method", "GET")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> options = HttpClient.newHttpClient().send(optionsRequest,
                HttpResponse.BodyHandlers.ofString());
        assertThat(options.statusCode()).isEqualTo(403);
        assertThat(options.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
        HttpRequest unauthenticatedOptionsRequest = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                        + "/api/v1/locations/provinces"))
                .timeout(Duration.ofSeconds(20))
                .header("Origin", "https://postman.example.test")
                .header("Access-Control-Request-Method", "GET")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> unauthenticatedOptions = HttpClient.newHttpClient().send(unauthenticatedOptionsRequest,
                HttpResponse.BodyHandlers.ofString());
        assertThat(unauthenticatedOptions.statusCode()).isEqualTo(401);
        assertThat(unauthenticatedOptions.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
        verifyNoInteractions(addressService);
    }

    @Test
    void rejectsMalformedAndSensitivePayloadsWithoutChangingRowsAndProvidesAuthenticatedLookups() throws Exception {
        UUID owner = account("CUSTOMER");
        String bearer = token(owner, "CUSTOMER");
        assertThat(send("PUT", ADDRESSES + "/" + UUID.randomUUID() + "/default", bearer, "{}")
                .statusCode()).isEqualTo(400);
        assertThat(send("GET", "/api/v1/locations/provinces", null, null).statusCode()).isEqualTo(401);
        HttpResponse<String> provinces = send("GET", "/api/v1/locations/provinces", bearer, null);
        assertThat(provinces.statusCode()).isEqualTo(200);
        assertThat(json(provinces).path("dataset_version").asText()).isEqualTo("v5.2.0");
        assertThat(json(provinces).path("provinces")).hasSize(34);
        HttpResponse<String> wards = send("GET", "/api/v1/locations/provinces/01/wards", bearer, null);
        assertThat(wards.statusCode()).isEqualTo(200);
        assertThat(json(wards).path("wards").get(0).path("code").isTextual()).isTrue();
        assertThat(send("GET", "/api/v1/locations/provinces/99/wards", bearer, null).statusCode()).isEqualTo(404);
        for (String bad : new String[] {"{}", "null", "[]", "{bad", "{\"recipient_name\":7,\"recipient_phone\":\"0912345678\",\"address_line\":\"A\"}",
                "{\"recipient_name\":\"A\",\"recipient_phone\":\"0912345678\",\"address_line\":\"A\",\"is_default\":true}",
                "{\"recipient_name\":\"A\",\"recipient_phone\":\"0912345678\",\"address_line\":\"A\",\"role\":\"ADMIN\"}",
                "{\"recipient_name\":\"A\",\"recipient_phone\":\"0912345678\",\"address_line\":\"A\",\"ward_code\":123}",
                "{\"recipient_name\":\"A\",\"recipient_phone\":\"0912345678\",\"address_line\":\"A\",\"ward_code\":\"99999\"}",
                "{\"recipient_name\":\"A\",\"recipient_phone\":\"0912ABC\",\"address_line\":\"A\"}"}) {
            HttpResponse<String> response = send("POST", ADDRESSES, bearer, bad);
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.body()).doesNotContain("password_hash", "recipient_phone", "address_line");
        }
        assertThat(count(owner)).isZero();
    }

    @Test
    void writeFailuresRollbackCreatePatchDefaultSwitchAndDelete() throws Exception {
        UUID owner = account("CUSTOMER");
        String bearer = token(owner, "CUSTOMER");
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("test-only insert failure after write");
        }).when(repository).insert(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(owner),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
        assertThat(send("POST", ADDRESSES, bearer, body(null, "A", "0912345678", "A", null)).statusCode()).isEqualTo(500);
        assertThat(count(owner)).isZero();
        reset(repository);

        UUID first = create(owner, bearer, "A");
        UUID second = create(owner, bearer, "B");
        var before = state(owner);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("test-only patch failure after write");
        }).when(repository).update(org.mockito.ArgumentMatchers.eq(owner), org.mockito.ArgumentMatchers.eq(second),
                org.mockito.ArgumentMatchers.any());
        assertThat(send("PATCH", ADDRESSES + "/" + second, bearer, "{\"label\":\"Changed\"}")
                .statusCode()).isEqualTo(500);
        assertThat(state(owner)).isEqualTo(before);
        reset(repository);

        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("test-only default failure after write");
        }).when(repository).setDefault(org.mockito.ArgumentMatchers.eq(owner), org.mockito.ArgumentMatchers.eq(second));
        assertThat(send("PUT", ADDRESSES + "/" + second + "/default", bearer, null).statusCode()).isEqualTo(500);
        assertThat(state(owner)).isEqualTo(before);
        reset(repository);

        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("test-only delete failure after write");
        }).when(repository).delete(org.mockito.ArgumentMatchers.eq(owner), org.mockito.ArgumentMatchers.eq(second));
        assertThat(send("DELETE", ADDRESSES + "/" + second, bearer, null).statusCode()).isEqualTo(500);
        assertThat(state(owner)).isEqualTo(before);
        assertThat(defaultCount(owner)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.customer_address WHERE address_id IN (?, ?)",
                Integer.class, first, second)).isEqualTo(2);
    }

    @Test
    void concurrentFirstCreatesSerializeOnTheDatabaseOwnerRow() throws Exception {
        UUID owner = account("CUSTOMER");
        String bearer = token(owner, "CUSTOMER");
        RaceResult race = runContendedPair(bearer, "POST", ADDRESSES, body("A", "A", "0912345678", "A", null),
                "POST", ADDRESSES, body("B", "B", "0912345678", "B", null));
        assertThat(race.a().statusCode()).isEqualTo(201);
        assertThat(race.b().statusCode()).isEqualTo(201);
        assertThat(json(race.a()).path("is_default").asBoolean()
                ^ json(race.b()).path("is_default").asBoolean()).isTrue();
        assertThat(count(owner)).isEqualTo(2);
        assertThat(defaultCount(owner)).isEqualTo(1);
        assertThat(addressIds(owner)).hasSize(2);
    }

    @Test
    void concurrentDefaultSwitchesAndDeleteSetDefaultRemainSerializable() throws Exception {
        UUID owner = account("CUSTOMER");
        String bearer = token(owner, "CUSTOMER");
        UUID first = create(owner, bearer, "A");
        UUID second = create(owner, bearer, "B");
        RaceResult defaults = runContendedPair(bearer, "PUT", ADDRESSES + "/" + first + "/default", null,
                "PUT", ADDRESSES + "/" + second + "/default", null);
        assertThat(defaults.a().statusCode()).isEqualTo(200);
        assertThat(defaults.b().statusCode()).isEqualTo(200);
        assertThat(json(defaults.a()).path("is_default").asBoolean()).isTrue();
        assertThat(json(defaults.b()).path("is_default").asBoolean()).isTrue();
        assertThat(defaultCount(owner)).isEqualTo(1);
        assertThat(count(owner)).isEqualTo(2);
        assertThat(addressIds(owner)).containsExactlyInAnyOrder(first, second);

        // Race deletion of current default with selecting its alternative. Either serial order is valid.
        UUID current = defaultAddress(owner);
        UUID alternative = current.equals(first) ? second : first;
        RaceResult deletion = runContendedPair(bearer, "DELETE", ADDRESSES + "/" + current, null,
                "PUT", ADDRESSES + "/" + alternative + "/default", null);
        assertThat(List.of(204, 409)).contains(deletion.a().statusCode());
        assertThat(deletion.b().statusCode()).isEqualTo(200);
        assertThat(json(deletion.b()).path("is_default").asBoolean()).isTrue();
        assertThat(defaultCount(owner)).isEqualTo(1);
        if (deletion.a().statusCode() == 204) {
            assertThat(addressIds(owner)).containsExactly(alternative);
        } else {
            assertThat(addressIds(owner)).containsExactlyInAnyOrder(current, alternative);
        }
    }

    @Test
    void concurrentPatchDeleteNeverResurrectsDeletedAddressAndDeleteCreateKeepsInvariant() throws Exception {
        UUID owner = account("CUSTOMER");
        String bearer = token(owner, "CUSTOMER");
        UUID first = create(owner, bearer, "A");
        UUID second = create(owner, bearer, "B");
        String firstBefore = addressState(owner, first);
        RaceResult patchDelete = runContendedPair(bearer, "PATCH", ADDRESSES + "/" + second,
                "{\"label\":\"Updated\"}",
                "DELETE", ADDRESSES + "/" + second, null);
        assertThat(List.of(200, 404)).contains(patchDelete.a().statusCode());
        assertThat(patchDelete.b().statusCode()).isEqualTo(204);
        assertThat(send("GET", ADDRESSES + "/" + second, bearer, null).statusCode()).isEqualTo(404);
        assertThat(addressIds(owner)).containsExactly(first);
        assertThat(addressState(owner, first)).isEqualTo(firstBefore);
        assertThat(defaultCount(owner)).isEqualTo(1);

        UUID remainingDefault = defaultAddress(owner);
        String createBody = body(null, "C", "0912345678", "C", null);
        String remainingBefore = addressState(owner, remainingDefault);
        RaceResult deleteCreate = runContendedPair(bearer, "DELETE", ADDRESSES + "/" + remainingDefault, null,
                "POST", ADDRESSES, createBody);
        assertThat(List.of(204, 409)).contains(deleteCreate.a().statusCode());
        assertThat(deleteCreate.b().statusCode()).isEqualTo(201);
        UUID created = UUID.fromString(json(deleteCreate.b()).path("address_id").asText());
        assertThat(defaultCount(owner)).isEqualTo(1);
        if (deleteCreate.a().statusCode() == 204) {
            assertThat(count(owner)).isEqualTo(1);
            assertThat(addressIds(owner)).containsExactly(created);
            assertThat(addressState(owner, created)).contains(":true");
        } else {
            assertThat(count(owner)).isEqualTo(2);
            assertThat(addressIds(owner)).containsExactlyInAnyOrder(remainingDefault, created);
            assertThat(addressState(owner, remainingDefault)).isEqualTo(remainingBefore);
            assertThat(addressState(owner, created)).contains(":false");
        }
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void preExistingBrokenDefaultInvariantIsNotRepairedByAddressWrites() throws Exception {
        UUID owner = account("CUSTOMER");
        String bearer = token(owner, "CUSTOMER");
        UUID first = rawAddress(owner, false, "A");
        UUID second = rawAddress(owner, false, "B");
        String before = state(owner);
        HttpResponse<String> response = send("PATCH", ADDRESSES + "/" + first, bearer, "{\"label\":\"Repair?\"}");
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(json(response).path("code").asText()).isEqualTo("INTERNAL_ERROR");
        assertThat(state(owner)).isEqualTo(before);
        assertThat(defaultCount(owner)).isZero();
        assertThat(count(owner)).isEqualTo(2);
        assertThat(response.body()).doesNotContain("recipient_phone", "address_line", "Repair?");
    }

    @Test
    void rejectsExpiredWrongIssuerWrongAudienceAndTamperedJwt() throws Exception {
        UUID owner = account("CUSTOMER");
        assertThat(send("GET", ADDRESSES, token(owner, "CUSTOMER", JwtTestSupport.ISSUER,
                JwtTestSupport.AUDIENCE, Instant.now().minusSeconds(30)), null).statusCode()).isEqualTo(401);
        assertThat(send("GET", ADDRESSES, token(owner, "CUSTOMER", "https://wrong.example",
                JwtTestSupport.AUDIENCE, Instant.now().plusSeconds(600)), null).statusCode()).isEqualTo(401);
        assertThat(send("GET", ADDRESSES, token(owner, "CUSTOMER", JwtTestSupport.ISSUER,
                "wrong-audience", Instant.now().plusSeconds(600)), null).statusCode()).isEqualTo(401);
        String valid = token(owner, "CUSTOMER");
        String[] parts = valid.split("\\.");
        String signature = parts[2];
        parts[2] = (signature.charAt(0) == 'A' ? "B" : "A") + signature.substring(1);
        String tampered = String.join(".", parts);
        assertThat(send("GET", ADDRESSES, tampered, null).statusCode()).isEqualTo(401);
    }

    private int awaitOwnerRowLockWait() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity "
                    + "WHERE pid <> pg_backend_pid() AND datname = current_database() "
                    + "AND state = 'active' AND wait_event_type = 'Lock' "
                    + "AND query ILIKE '%public.account%' AND query ILIKE '%FOR UPDATE%'", Integer.class);
            if (waiting != null && waiting > 0) return waiting;
            Thread.sleep(10);
        }
        throw new AssertionError("Second write did not wait for the PostgreSQL owner row lock");
    }

    private RaceResult runContendedPair(String bearer, String methodA, String pathA, String bodyA,
            String methodB, String pathB, String bodyB) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch transactionHoldingOwnerLock = new CountDownLatch(1);
        CountDownLatch releaseTransaction = new CountDownLatch(1);
        AtomicInteger findAllCalls = new AtomicInteger();
        doAnswer(invocation -> {
            if (findAllCalls.incrementAndGet() == 1) {
                transactionHoldingOwnerLock.countDown();
                if (!releaseTransaction.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("Timed out waiting to release the first address transaction");
                }
            }
            return invocation.callRealMethod();
        }).when(repository).findAll(org.mockito.ArgumentMatchers.any());
        CompletableFuture<HttpResponse<String>> a = concurrentRequest(bearer, methodA, pathA, bodyA, ready, start);
        CompletableFuture<HttpResponse<String>> b = concurrentRequest(bearer, methodB, pathB, bodyB, ready, start);
        try {
            assertThat(ready.await(5, TimeUnit.SECONDS)).as("both requests reach the start barrier").isTrue();
            start.countDown();
            assertThat(transactionHoldingOwnerLock.await(10, TimeUnit.SECONDS))
                    .as("one transaction holds the owner lock before reading addresses").isTrue();
            assertThat(awaitOwnerRowLockWait()).as("PostgreSQL reports the competing transaction waiting").isGreaterThan(0);
        } finally {
            start.countDown();
            releaseTransaction.countDown();
        }
        return new RaceResult(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
    }

    private record RaceResult(HttpResponse<String> a, HttpResponse<String> b) { }

    private CompletableFuture<HttpResponse<String>> concurrentRequest(String bearer, String method, String path,
            String body, CountDownLatch ready, CountDownLatch start) {
        return CompletableFuture.supplyAsync(() -> {
            ready.countDown();
            try {
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start barrier timeout");
                return send(method, path, bearer, body);
            } catch (Exception exception) { throw new RuntimeException(exception); }
        });
    }

    private UUID create(UUID owner, String bearer, String label) throws Exception {
        HttpResponse<String> response = send("POST", ADDRESSES, bearer, body(label, label, "0912345678", label, null));
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(json(response).path("address_id").asText());
    }

    private String state(UUID owner) {
        return String.join("|", jdbc.query("SELECT address_id || ':' || coalesce(label,'<null>') || ':' || recipient_name"
                + " || ':' || recipient_phone || ':' || address_line || ':' || coalesce(ward_code,'<null>')"
                + " || ':' || is_default FROM public.customer_address WHERE account_id = ? ORDER BY address_id",
                (rs, row) -> rs.getString(1), owner));
    }

    private String addressState(UUID owner, UUID addressId) {
        return jdbc.queryForObject("SELECT address_id || ':' || coalesce(label,'<null>') || ':' || recipient_name"
                + " || ':' || recipient_phone || ':' || address_line || ':' || coalesce(ward_code,'<null>')"
                + " || ':' || is_default FROM public.customer_address WHERE account_id = ? AND address_id = ?",
                String.class, owner, addressId);
    }

    private List<UUID> addressIds(UUID owner) {
        return jdbc.query("SELECT address_id FROM public.customer_address WHERE account_id = ? ORDER BY address_id",
                (rs, row) -> rs.getObject(1, UUID.class), owner);
    }

    private int count(UUID owner) {
        return jdbc.queryForObject("SELECT count(*) FROM public.customer_address WHERE account_id = ?", Integer.class, owner);
    }
    private int defaultCount(UUID owner) {
        return jdbc.queryForObject("SELECT count(*) FROM public.customer_address WHERE account_id = ? AND is_default",
                Integer.class, owner);
    }
    private UUID defaultAddress(UUID owner) {
        return jdbc.queryForObject("SELECT address_id FROM public.customer_address WHERE account_id = ? AND is_default",
                UUID.class, owner);
    }

    private UUID account(String... roles) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.account (account_id, email, password_hash, account_status, registered_at) "
                + "VALUES (?, ?, ?, 'ACTIVE', now())", id, "address-" + id + "@example.test", "test-only-hash");
        for (String role : roles) jdbc.update("INSERT INTO public.account_role (account_id, role_code) VALUES (?, ?)", id, role);
        return id;
    }

    private java.util.Map<String, Object> accountRow(UUID accountId) {
        return jdbc.queryForMap("SELECT account_id, email, password_hash, account_status, email_verified_at, registered_at "
                + "FROM public.account WHERE account_id = ?", accountId);
    }

    private List<String> roles(UUID accountId) {
        return jdbc.query("SELECT role_code FROM public.account_role WHERE account_id = ? ORDER BY role_code",
                (rs, row) -> rs.getString(1), accountId);
    }

    private int unrelatedRows(UUID accountId) {
        return jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM public.user_profile WHERE account_id = ?)
                     + (SELECT count(*) FROM public.style_profile WHERE account_id = ?)
                     + (SELECT count(*) FROM public.profile_style WHERE account_id = ?)
                     + (SELECT count(*) FROM public.profile_color WHERE account_id = ?)
                     + (SELECT count(*) FROM public.profile_brand WHERE account_id = ?)
                     + (SELECT count(*) FROM public.refresh_session WHERE account_id = ?)
                     + (SELECT count(*) FROM public.email_verification_challenge WHERE account_id = ?)
                     + (SELECT count(*) FROM public.password_reset_challenge WHERE account_id = ?)
                """, Integer.class, accountId, accountId, accountId, accountId, accountId, accountId, accountId, accountId);
    }

    private UUID rawAddress(UUID owner, boolean isDefault, String label) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.customer_address (address_id,account_id,label,recipient_name,recipient_phone,"
                + "address_line,ward_code,is_default) VALUES (?,?,?,?,?,?,?,?)", id, owner, label, label,
                "+84912345678", label, null, isDefault);
        return id;
    }

    private String token(UUID subject, String role) throws Exception {
        return token(subject, role, JwtTestSupport.ISSUER, JwtTestSupport.AUDIENCE, Instant.now().plusSeconds(600));
    }

    private String token(UUID subject, String role, String issuer, String audience, Instant expiresAt) throws Exception {
        Instant now = Instant.now();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(JwtTestSupport.SIGNING_KEY.getKeyID()).build(),
                new JWTClaimsSet.Builder().issuer(issuer).audience(audience)
                        .subject(subject.toString()).jwtID(UUID.randomUUID().toString())
                        .issueTime(Date.from(now.minusSeconds(1))).expirationTime(Date.from(expiresAt))
                        .claim("roles", List.of(role)).build());
        jwt.sign(new RSASSASigner(JwtTestSupport.SIGNING_KEY.toPrivateKey()));
        return jwt.serialize();
    }

    private HttpResponse<String> send(String method, String path, String bearer, String body) throws Exception {
        return send(method, path, bearer, body, "");
    }

    private HttpResponse<String> sendHead(String path, String bearer) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20)).header("X-Correlation-Id", "address-head-test-" + UUID.randomUUID());
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        return HttpClient.newHttpClient().send(request.method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> send(String method, String path, String bearer, String body, String query) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path + query))
                .timeout(Duration.ofSeconds(20)).header("X-Correlation-Id", "address-test-" + UUID.randomUUID());
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
        else request.header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .method(method, HttpRequest.BodyPublishers.ofString(body));
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> uncheckedSend(String method, String path, String bearer, String body) {
        try { return send(method, path, bearer, body); }
        catch (Exception exception) { throw new RuntimeException(exception); }
    }
    private JsonNode json(HttpResponse<String> response) throws Exception { return mapper.readTree(response.body()); }

    private static String body(String label, String name, String phone, String line, String ward) {
        String labelJson = label == null ? "null" : "\"" + label + "\"";
        String wardJson = ward == null ? "null" : "\"" + ward + "\"";
        return "{\"label\":" + labelJson + ",\"recipient_name\":\"" + name + "\",\"recipient_phone\":\""
                + phone + "\",\"address_line\":\"" + line + "\",\"ward_code\":" + wardJson + "}";
    }
}
