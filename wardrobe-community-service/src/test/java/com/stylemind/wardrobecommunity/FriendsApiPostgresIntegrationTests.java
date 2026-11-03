package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.stylemind.wardrobecommunity.client.identity.IdentityDirectoryClient;
import com.stylemind.wardrobecommunity.client.identity.payload.AccountLookupItem;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.model.friendship.FriendshipPair;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.health.db.enabled=false")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Testcontainers
@Import(FriendsApiPostgresIntegrationTests.JwtTestConfiguration.class)
class FriendsApiPostgresIntegrationTests {

    private static final String FRIENDS = "/api/v1/me/friends";
    private static final String INVITATIONS = "/api/v1/me/friend-invitations";
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);
    private static final UUID D = new UUID(0, 4);
    private static final Instant NOW = Instant.parse("2026-10-24T10:00:00.123456789Z");
    private static final OffsetDateTime DB_NOW = OffsetDateTime.ofInstant(NOW.truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
    private static final OffsetDateTime ESTABLISHED = OffsetDateTime.parse("2026-10-01T09:00:00Z");
    private static final RSAKey SIGNING_KEY = signingKey();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6")
            .withDatabaseName("stylemind_friends_api_test")
            .withUsername("friends_api_test")
            .withPassword("test-only-not-a-secret");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private MockMvc mvc;
    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private IdentityDirectoryClient identity;
    @MockitoBean(name = "friendshipClock") private Clock clock;
    private final Map<UUID, AccountLookupItem> accounts = new HashMap<>();

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM public.friend_invitation");
        jdbc.update("DELETE FROM public.friendship");
        accounts.clear();
        accounts.put(A, new AccountLookupItem(A, "private-a@example.test", "Linh", "ACTIVE"));
        accounts.put(B, new AccountLookupItem(B, "private-b@example.test", "Minh", "ACTIVE"));
        accounts.put(C, new AccountLookupItem(C, "private-c@example.test", "Mai", "ACTIVE"));
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(identity.lookup(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            Collection<UUID> ids = invocation.getArgument(0);
            assertThat(ids.size()).isLessThanOrEqualTo(100);
            Map<UUID, AccountLookupItem> found = new LinkedHashMap<>();
            ids.forEach(id -> { if (accounts.containsKey(id)) { found.put(id, accounts.get(id)); } });
            return found;
        });
    }

    @Test
    void allRoutesRequireSignedCustomerJwtAndUuidSubject() throws Exception {
        List<Supplier<MockHttpServletRequestBuilder>> routes = List.of(
                () -> get(FRIENDS), () -> get(INVITATIONS), () -> delete(FRIENDS + "/" + B));
        for (var route : routes) {
            mvc.perform(route.get()).andExpect(status().isUnauthorized());
            mvc.perform(route.get().header(HttpHeaders.AUTHORIZATION, "Bearer invalid-test-bearer"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_BEARER_TOKEN"));
            mvc.perform(route.get().header(HttpHeaders.AUTHORIZATION, "Bearer " + token(A.toString(), List.of("ADMIN"), Instant.now().plusSeconds(600))))
                    .andExpect(status().isForbidden());
            mvc.perform(route.get().header(HttpHeaders.AUTHORIZATION, "Bearer " + token("not-a-uuid", List.of("CUSTOMER"), Instant.now().plusSeconds(600))))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
            mvc.perform(route.get().header(HttpHeaders.AUTHORIZATION, "Bearer " + token(A.toString(), List.of("CUSTOMER"), Instant.now().minusSeconds(120))))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(identity);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.friendship", Integer.class)).isZero();
    }

    @Test
    void customerWithAdditionalAdminRoleCanStillReadTheirOwnData() throws Exception {
        mvc.perform(get(FRIENDS).header(HttpHeaders.AUTHORIZATION,
                "Bearer " + token(A.toString(), List.of("ADMIN", "CUSTOMER"), Instant.now().plusSeconds(600))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total_elements").value(0));
    }

    @Test
    void emptyListsAreSafeAndNeedNoIdentityLookup() throws Exception {
        for (String route : List.of(FRIENDS, INVITATIONS)) {
            send(get(route), A).andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                    .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.total_elements").value(0))
                    .andExpect(jsonPath("$.total_pages").value(0));
        }
        verifyNoInteractions(identity);
    }

    @Test
    void activeFriendsAreActorScopedAndDoNotExposePeerEmail() throws Exception {
        friendship(A, B, ESTABLISHED);
        UUID inactive = friendship(A, C, ESTABLISHED);
        jdbc.update("UPDATE public.friendship SET unfriended_at = ?, updated_at = ? WHERE friendship_id = ?", DB_NOW, DB_NOW, inactive);
        friendship(C, D, ESTABLISHED);

        JsonNode forA = body(send(get(FRIENDS), A).andExpect(status().isOk()));
        JsonNode forB = body(send(get(FRIENDS), B).andExpect(status().isOk()));

        assertThat(forA.path("items")).hasSize(1);
        assertThat(forA.at("/items/0/friend_account_id").asText()).isEqualTo(B.toString());
        assertThat(forA.at("/items/0/display_name").asText()).isEqualTo("Minh");
        assertThat(forB.at("/items/0/friend_account_id").asText()).isEqualTo(A.toString());
        assertThat(fields(forA.at("/items/0"))).containsExactlyInAnyOrder("friendship_id", "friend_account_id", "display_name", "established_at");
    }

    @Test
    void pagesAreStableAndHugeOffsetsDoNotOverflow() throws Exception {
        friendship(A, B, ESTABLISHED);
        friendship(A, C, ESTABLISHED);
        friendship(A, D, ESTABLISHED);
        JsonNode first = body(send(get(FRIENDS).param("page", "0").param("size", "1"), A).andExpect(status().isOk()));
        JsonNode next = body(send(get(FRIENDS).param("page", "1").param("size", "1"), A).andExpect(status().isOk()));
        assertThat(first.path("total_elements").asInt()).isEqualTo(3);
        assertThat(first.path("total_pages").asInt()).isEqualTo(3);
        assertThat(first.at("/items/0/friendship_id").asText()).isNotEqualTo(next.at("/items/0/friendship_id").asText());
        assertThat(body(send(get(FRIENDS).param("size", "1"), A)).at("/items/0/friendship_id")).isEqualTo(first.at("/items/0/friendship_id"));
        send(get(FRIENDS).param("page", "2147483647").param("size", "100"), A)
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.total_elements").value(3));
    }

    @Test
    void unknownIdentityProfileKeepsTheFriendWithNullDisplayName() throws Exception {
        friendship(A, D, ESTABLISHED);

        JsonNode result = body(send(get(FRIENDS), A).andExpect(status().isOk()));

        assertThat(result.at("/items/0/friend_account_id").asText()).isEqualTo(D.toString());
        assertThat(result.at("/items/0/display_name").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.friendship", Integer.class)).isEqualTo(1);
    }

    @Test
    void outgoingInvitationsShowEffectiveExpiryWithoutWritingOrLeakingCredentials() throws Exception {
        UUID pending = invitation(A, "pending@example.test", DB_NOW.plusNanos(1000));
        UUID expired = invitation(A, "expired@example.test", DB_NOW);
        invitation(C, "private@example.test", DB_NOW.plusDays(1));

        JsonNode result = body(send(get(INVITATIONS), A).andExpect(status().isOk()));

        assertThat(result.path("total_elements").asInt()).isEqualTo(2);
        Map<String, String> states = new HashMap<>();
        for (JsonNode item : result.path("items")) {
            assertThat(fields(item)).containsExactlyInAnyOrder("friend_invitation_id", "recipient_email", "status", "created_at", "expires_at", "accepted_at");
            states.put(item.path("friend_invitation_id").asText(), item.path("status").asText());
        }
        assertThat(states).containsEntry(pending.toString(), "PENDING").containsEntry(expired.toString(), "EXPIRED");
        assertThat(jdbc.queryForObject("SELECT status FROM public.friend_invitation WHERE friend_invitation_id = ?", String.class, expired)).isEqualTo("PENDING");
        send(get(INVITATIONS), B).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        verifyNoInteractions(identity);
    }

    @Test
    void invalidQueriesAndOwnerOverridesCannotWriteData() throws Exception {
        UUID pair = friendship(A, B, ESTABLISHED);
        String before = fingerprint(pair);
        for (String route : List.of(FRIENDS, INVITATIONS)) {
            for (String[] invalid : new String[][] {{"size", "101"}, {"size", "0"}, {"page", "-1"},
                    {"page", "2147483648"}, {"owner_id", C.toString()}, {"sender_account_id", C.toString()}}) {
                send(get(route).param(invalid[0], invalid[1]), A).andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
            }
            send(get(route).param("page", "0", "1"), A).andExpect(status().isBadRequest());
        }
        send(delete(FRIENDS + "/" + B).param("owner_id", C.toString()), A).andExpect(status().isBadRequest());
        assertThat(fingerprint(pair)).isEqualTo(before);
    }

    @Test
    void selfMalformedAndForeignUnfriendDoNotChangeThePair() throws Exception {
        UUID pair = friendship(A, B, ESTABLISHED);
        String before = fingerprint(pair);
        for (String invalid : List.of(A.toString(), "not-a-uuid", "1-1-1-1-1")) {
            send(delete(FRIENDS + "/" + invalid), A).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        send(delete(FRIENDS + "/" + B), C).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        send(delete(FRIENDS + "/" + D), A).andExpect(status().isNotFound());
        assertThat(fingerprint(pair)).isEqualTo(before);
    }

    @Test
    void unfriendIsBidirectionalKeepsTheRowAndPreservesOtherPairsAndInvitations() throws Exception {
        UUID target = friendship(A, B, ESTABLISHED);
        UUID other = friendship(A, C, ESTABLISHED);
        UUID invitation = invitation(A, "existing@example.test", DB_NOW.plusDays(1));
        String otherBefore = fingerprint(other);

        send(delete(FRIENDS + "/" + A), B).andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));

        assertThat(jdbc.queryForObject("SELECT unfriended_at FROM public.friendship WHERE friendship_id = ?", OffsetDateTime.class, target)).isEqualTo(DB_NOW);
        assertThat(fingerprint(other)).isEqualTo(otherBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.friend_invitation WHERE friend_invitation_id = ?", Integer.class, invitation)).isEqualTo(1);
        send(get(FRIENDS), B).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        send(get(FRIENDS), A).andExpect(status().isOk()).andExpect(jsonPath("$.total_elements").value(1));
        send(delete(FRIENDS + "/" + B), A).andExpect(status().isNotFound());
    }

    @Test
    void identityFailureDoesNotBlockLocalInvitationListOrUnfriend() throws Exception {
        friendship(A, B, ESTABLISHED);
        doThrow(new ApiOperationException(HttpStatus.SERVICE_UNAVAILABLE,
                "IDENTITY_UNAVAILABLE", "Account directory is temporarily unavailable")).when(identity).lookup(any());

        send(get(FRIENDS), A).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("IDENTITY_UNAVAILABLE"));
        send(get(INVITATIONS), A).andExpect(status().isOk());
        send(delete(FRIENDS + "/" + B), A).andExpect(status().isNoContent());
    }

    @Test
    void failedDatabaseWriteRollsBackTheUnfriend() throws Exception {
        UUID pair = friendship(A, B, ESTABLISHED);
        String before = fingerprint(pair);
        jdbc.execute("CREATE FUNCTION fixture_fail_unfriend() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'fixture write failure'; END; $$");
        jdbc.execute("CREATE TRIGGER fixture_fail_unfriend BEFORE UPDATE ON public.friendship FOR EACH ROW EXECUTE FUNCTION fixture_fail_unfriend()");
        try {
            send(delete(FRIENDS + "/" + B), A).andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        } finally {
            jdbc.execute("DROP TRIGGER fixture_fail_unfriend ON public.friendship");
            jdbc.execute("DROP FUNCTION fixture_fail_unfriend()");
        }

        assertThat(fingerprint(pair)).isEqualTo(before);
    }

    @Test
    void realHttpExercisesAllThreeRoutesWithSignedJwtAndPostgres() throws Exception {
        friendship(A, B, ESTABLISHED);
        invitation(A, "sent@example.test", DB_NOW.plusDays(1));

        var friends = http.exchange(FRIENDS, HttpMethod.GET, authenticated(A), String.class);
        assertThat(friends.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(friends.getBody()).at("/items/0/friend_account_id").asText()).isEqualTo(B.toString());
        assertThat(http.exchange(INVITATIONS, HttpMethod.GET, authenticated(A), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.exchange(FRIENDS + "/" + B, HttpMethod.DELETE, authenticated(C), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(http.exchange(FRIENDS + "/" + B, HttpMethod.DELETE, authenticated(A), String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        var after = http.exchange(FRIENDS, HttpMethod.GET, authenticated(B), String.class);
        assertThat(mapper.readTree(after.getBody()).path("items")).isEmpty();
    }

    @Test
    void concurrentUnfriendOverRealHttpHasOneWinnerAndKeepsOneInactivePair() throws Exception {
        UUID pair = friendship(A, B, ESTABLISHED);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> concurrentUnfriend(A, B, ready, start));
            var second = workers.submit(() -> concurrentUnfriend(B, A, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(204, 404);
        } finally {
            start.countDown();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.friendship WHERE friendship_id = ? AND unfriended_at IS NOT NULL", Integer.class, pair)).isEqualTo(1);
    }

    @Test
    void openApiDescribesTheImplementedRoutesAndSafeResponseSchemas() throws Exception {
        JsonNode document = body(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));
        JsonNode paths = document.path("paths");
        assertThat(paths.path(FRIENDS).path("get").path("parameters")).hasSize(2);
        assertThat(paths.path(INVITATIONS).path("get").path("summary").asText()).contains("sent");
        assertThat(paths.path(FRIENDS + "/{friendAccountId}").path("delete").path("responses").has("204")).isTrue();
        assertThat(document.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
        assertThat(fields(document.at("/components/schemas/FriendInvitationSummaryResponse/properties")))
                .containsExactlyInAnyOrder("friend_invitation_id", "recipient_email", "status", "created_at", "expires_at", "accepted_at");
        assertThat(fields(document.at("/components/schemas/FriendSummaryResponse/properties")))
                .doesNotContain("email", "token", "token_hash");
    }

    @Test
    void invitationCreateRejectsTheObsoleteEmailFieldWithoutWriting() throws Exception {
        send(post(INVITATIONS).contentType("application/json").content("{\"email\":\"pending@example.test\"}"), A)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.friend_invitation", Integer.class)).isZero();
    }

    @Test
    void unfriendKeepsAMonotonicCutoffWhenTheClockMovesBackwards() throws Exception {
        UUID pair = friendship(A, B, ESTABLISHED);
        when(clock.instant()).thenReturn(ESTABLISHED.minusSeconds(1).toInstant());
        send(delete(FRIENDS + "/" + B), A).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT unfriended_at = established_at AND invitations_invalid_before = established_at FROM public.friendship WHERE friendship_id = ?",
                Boolean.class, pair)).isTrue();
    }

    private int concurrentUnfriend(UUID actor, UUID peer, CountDownLatch ready, CountDownLatch start) throws Exception {
        HttpEntity<Void> request = authenticated(actor);
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Test start timed out");
        }
        return http.exchange(FRIENDS + "/" + peer, HttpMethod.DELETE, request, String.class).getStatusCode().value();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, UUID actor) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token(actor.toString(), List.of("CUSTOMER"), Instant.now().plusSeconds(600))));
    }

    private HttpEntity<Void> authenticated(UUID actor) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token(actor.toString(), List.of("CUSTOMER"), Instant.now().plusSeconds(600)));
        return new HttpEntity<>(headers);
    }

    private JsonNode body(ResultActions response) throws Exception {
        return mapper.readTree(response.andReturn().getResponse().getContentAsByteArray());
    }

    private List<String> fields(JsonNode value) {
        List<String> fields = new ArrayList<>();
        value.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    private UUID friendship(UUID first, UUID second, OffsetDateTime at) {
        FriendshipPair pair = new FriendshipPair(first, second);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public.friendship (friendship_id, account_low_id, account_high_id, established_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, pair.accountLowId(), pair.accountHighId(), at, at, at);
        return id;
    }

    private UUID invitation(UUID sender, String recipient, OffsetDateTime expiresAt) {
        UUID id = UUID.randomUUID();
        OffsetDateTime issued = expiresAt.minusHours(168);
        String fixtureHash = id.toString().replace("-", "").repeat(2);
        jdbc.update("""
                INSERT INTO public.friend_invitation (friend_invitation_id, sender_account_id, recipient_email, status,
                    token_hash, token_issued_at, expires_at, created_at, updated_at)
                VALUES (?, ?, ?, 'PENDING', ?, ?, ?, ?, ?)
                """, id, sender, recipient, fixtureHash, issued, expiresAt, issued, issued);
        return id;
    }

    private String fingerprint(UUID id) {
        return jdbc.queryForObject("SELECT md5(row_to_json(f)::text) FROM public.friendship f WHERE friendship_id = ?", String.class, id);
    }

    private String token(String subject, List<String> roles, Instant expiresAt) {
        var claims = JwtClaimsSet.builder().subject(subject).issuedAt(Instant.now().minusSeconds(300))
                .expiresAt(expiresAt).claim("roles", roles).build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(SIGNING_KEY.getKeyID()).build();
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(SIGNING_KEY)));
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static RSAKey signingKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID(UUID.randomUUID().toString()).build();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JwtTestConfiguration {
        @Bean
        @Primary
        JwtDecoder friendsTestJwtDecoder() throws Exception {
            return NimbusJwtDecoder.withPublicKey(SIGNING_KEY.toRSAPublicKey()).build();
        }
    }
}
