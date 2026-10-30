package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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
import com.stylemind.wardrobecommunity.client.notification.ShareInvitationMailClient;
import com.stylemind.wardrobecommunity.entity.ChatParticipant;
import com.stylemind.wardrobecommunity.model.friendship.FriendshipPair;
import com.stylemind.wardrobecommunity.repository.chat.ChatParticipantRepository;
import com.stylemind.wardrobecommunity.repository.friendship.FriendshipRepository;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.http.MediaType;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.health.db.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Testcontainers
@Import(ChatSessionApiPostgresIntegrationTests.JwtTestConfiguration.class)
class ChatSessionApiPostgresIntegrationTests {
    private static final String CHATS = "/api/v1/me/chats";
    private static final String DETAIL = "/api/v1/chats/";
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = UUID.fromString("80000000-0000-0000-0000-000000000002");
    private static final UUID C = UUID.fromString("ffffffff-0000-0000-0000-000000000003");
    private static final UUID D = new UUID(0, 4);
    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00.123456789Z");
    private static final OffsetDateTime DB_NOW = OffsetDateTime.ofInstant(NOW.truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
    private static final OffsetDateTime EARLIER = OffsetDateTime.parse("2026-10-01T09:00:00Z");
    private static final RSAKey KEY = signingKey();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6")
            .withDatabaseName("stylemind_chat_session_api_test")
            .withUsername("chat_session_api_test")
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
    @Autowired private TransactionTemplate transaction;
    @Autowired private FriendshipRepository friendships;
    @MockitoBean private IdentityDirectoryClient identity;
    @MockitoBean private ShareInvitationMailClient mail;
    @MockitoBean(name = "chatClock") private Clock chatClock;
    @MockitoBean(name = "friendshipClock") private Clock friendshipClock;
    @MockitoSpyBean private ChatParticipantRepository participants;

    @BeforeEach
    void resetDisposableDatabase() {
        jdbc.update("DELETE FROM public.chat_attachment");
        jdbc.update("DELETE FROM public.chat_message");
        jdbc.update("DELETE FROM public.chat_participant");
        jdbc.update("DELETE FROM public.chat_session");
        jdbc.update("DELETE FROM public.friend_invitation");
        jdbc.update("DELETE FROM public.friendship");
        for (Clock clock : List.of(chatClock, friendshipClock)) {
            when(clock.instant()).thenReturn(NOW);
            when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        }
    }

    @AfterEach
    void noCrossServiceDependencyOrEmailSideEffects() {
        verifyNoInteractions(identity, mail);
    }

    @Test
    void allThreeRoutesRequireSignedCustomerJwtAndUuidSubject() throws Exception {
        for (Supplier<MockHttpServletRequestBuilder> route : routes()) {
            mvc.perform(route.get()).andExpect(status().isUnauthorized());
            mvc.perform(route.get().header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(route.get().header(HttpHeaders.AUTHORIZATION,
                    "Bearer " + token(A.toString(), List.of("CUSTOMER"), Instant.now().minusSeconds(120))))
                    .andExpect(status().isUnauthorized());
            mvc.perform(route.get().header(HttpHeaders.AUTHORIZATION,
                    "Bearer " + token("not-a-uuid", List.of("CUSTOMER"), Instant.now().plusSeconds(600))))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
            for (List<String> roles : List.of(List.of("ADMIN"), List.of("UNKNOWN"), List.<String>of())) {
                mvc.perform(route.get().header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + token(A.toString(), roles, Instant.now().plusSeconds(600))))
                        .andExpect(status().isForbidden());
            }
        }
        assertThat(count("chat_session")).isZero();
    }

    @Test
    void customerAndAdminCombinationRemainsSelfScoped() throws Exception {
        friendship(A, B);
        var response = mvc.perform(openRequest(B).header(HttpHeaders.AUTHORIZATION,
                "Bearer " + token(A.toString(), List.of("CUSTOMER", "ADMIN"), Instant.now().plusSeconds(600))))
                .andExpect(status().isCreated());
        UUID id = id(body(response));
        send(get(DETAIL + id), C).andExpect(status().isNotFound());
        send(get(DETAIL + id), A).andExpect(status().isOk());
    }

    @Test
    void createsExactlyOneSessionAndBothCanonicalMembersWithMicrosecondUtcTime() throws Exception {
        UUID pairId = friendship(A, B);
        ResultActions response = send(openRequest(B), A).andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        JsonNode json = body(response);
        UUID sessionId = id(json);

        assertThat(fields(json)).containsExactlyInAnyOrder("chat_session_id", "friend_account_id", "created_at", "updated_at");
        assertThat(json.path("friend_account_id").asText()).isEqualTo(B.toString());
        assertThat(OffsetDateTime.parse(json.path("created_at").asText())).isEqualTo(DB_NOW);
        assertThat(response.andReturn().getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(DETAIL + sessionId);
        assertThat(jdbc.queryForObject("SELECT friendship_id FROM public.chat_session WHERE chat_session_id = ?",
                UUID.class, sessionId)).isEqualTo(pairId);
        assertThat(jdbc.queryForList("SELECT account_id FROM public.chat_participant ORDER BY participant_slot", UUID.class))
                .containsExactly(A, B);
        assertThat(count("chat_session")).isEqualTo(1);
        assertThat(count("chat_participant")).isEqualTo(2);
        assertThat(count("chat_message")).isZero();
        assertThat(count("chat_attachment")).isZero();
    }

    @Test
    void reopeningInBothDirectionsReturnsSameIdWithoutChangingAnyRowsOrTimestamps() throws Exception {
        friendship(A, B);
        UUID sessionId = open(A, B);
        List<String> before = fingerprints();
        when(chatClock.instant()).thenReturn(NOW.plusSeconds(500));

        JsonNode fromA = body(send(openRequest(B), A).andExpect(status().isOk()));
        JsonNode fromB = body(send(openRequest(A), B).andExpect(status().isOk()));
        assertThat(id(fromA)).isEqualTo(sessionId);
        assertThat(id(fromB)).isEqualTo(sessionId);
        assertThat(fromA.path("friend_account_id").asText()).isEqualTo(B.toString());
        assertThat(fromB.path("friend_account_id").asText()).isEqualTo(A.toString());
        assertThat(fingerprints()).isEqualTo(before);
    }

    @Test
    void readsDoNotCreateSessionsEvenWhenTheCallerHasFriends() throws Exception {
        friendship(A, B);
        List<String> before = fingerprints();
        send(get(CHATS), A).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.total_elements").value(0))
                .andExpect(jsonPath("$.total_pages").value(0));
        send(get(DETAIL + UUID.randomUUID()), A).andExpect(status().isNotFound());
        assertThat(fingerprints()).isEqualTo(before);
    }

    @Test
    void selfUnknownNonFriendPendingOnlyAndInactivePairsCannotOpen() throws Exception {
        send(openRequest(A), A).andExpect(status().isBadRequest());
        send(openRequest(D), A).andExpect(status().isNotFound());
        jdbc.update("""
                INSERT INTO public.friend_invitation
                  (friend_invitation_id, sender_account_id, recipient_email, status, token_hash,
                   token_issued_at, expires_at, created_at, updated_at)
                VALUES (?, ?, 'pending@example.test', 'PENDING', ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), A, "a".repeat(64), DB_NOW, DB_NOW.plusHours(168), DB_NOW, DB_NOW);
        send(openRequest(B), A).andExpect(status().isNotFound());
        UUID inactive = friendship(A, B);
        jdbc.update("UPDATE public.friendship SET unfriended_at = ?, updated_at = ? WHERE friendship_id = ?",
                DB_NOW, DB_NOW, inactive);
        send(openRequest(B), A).andExpect(status().isNotFound());
        send(openRequest(A), B).andExpect(status().isNotFound());
        assertThat(count("chat_session")).isZero();
    }

    @Test
    void outsiderCannotGuessAnotherSessionEvenIfTheyAreFriendsWithOneParticipant() throws Exception {
        friendship(A, B);
        friendship(A, C);
        UUID sessionId = open(A, B);
        List<String> before = fingerprints();
        send(get(DETAIL + sessionId), C).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        send(get(CHATS), C).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total_elements").value(0));
        send(get(DETAIL + UUID.randomUUID()), C).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        assertThat(fingerprints()).isEqualTo(before);
    }

    @Test
    void invalidJsonAndActorOverridesAreRejectedWithoutWrites() throws Exception {
        friendship(A, B);
        List<String> before = fingerprints();
        for (String raw : List.of("{}", "null", "[]", "{\"friend_account_id\":null}", "{\"friend_account_id\":7}",
                "{\"friend_account_id\":\"1-1-1-1-1\"}", "{bad}",
                payload(B) + "{}", "{\"friend_account_id\":\"" + B + "\",\"friend_account_id\":\"" + B + "\"}",
                "{\"friend_account_id\":\"" + B + "\",\"actor_id\":\"" + C + "\"}")) {
            send(post(CHATS).contentType(MediaType.APPLICATION_JSON).content(raw), A)
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        send(post(CHATS).contentType(MediaType.APPLICATION_JSON), A).andExpect(status().isBadRequest());
        assertThat(fingerprints()).isEqualTo(before);
    }

    @Test
    void badQueryDuplicatePaginationAndMalformedPathUuidReturn400() throws Exception {
        friendship(A, B);
        UUID sessionId = open(A, B);
        for (String query : List.of("?owner=" + C, "?page=-1", "?size=0", "?size=101", "?page=2147483648",
                "?size=abc", "?page=0&page=1", "?size=20&size=20")) {
            send(get(CHATS + query), A).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        send(get(DETAIL + "1-1-1-1-1"), A).andExpect(status().isBadRequest());
        send(get(DETAIL + sessionId + "?owner=" + C), A).andExpect(status().isBadRequest());
        send(openRequest(B).queryParam("owner", C.toString()), A).andExpect(status().isBadRequest());
    }

    @Test
    void postRequiresApplicationJson() throws Exception {
        send(post(CHATS).contentType(MediaType.TEXT_PLAIN).content(payload(B)), A)
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void listFiltersByActorActiveFriendshipAndStableUpdatedTimeWithUuidTies() throws Exception {
        friendship(A, B);
        friendship(A, C);
        friendship(B, C);
        UUID ab = open(A, B);
        UUID ac = open(A, C);
        open(B, C);
        List<UUID> expected = jdbc.queryForList("""
                SELECT chat_session_id FROM public.chat_session
                WHERE chat_session_id IN (?, ?) ORDER BY updated_at DESC, chat_session_id DESC
                """, UUID.class, ab, ac);
        JsonNode first = body(send(get(CHATS + "?size=1"), A).andExpect(status().isOk()));
        JsonNode second = body(send(get(CHATS + "?size=1&page=1"), A).andExpect(status().isOk()));

        assertThat(first.path("total_elements").asInt()).isEqualTo(2);
        assertThat(first.path("total_pages").asInt()).isEqualTo(2);
        assertThat(id(first.path("items").get(0))).isEqualTo(expected.get(0));
        assertThat(id(second.path("items").get(0))).isEqualTo(expected.get(1));
        send(get(CHATS + "?page=2147483647&size=100"), A).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.total_elements").value(2));
        jdbc.update("UPDATE public.chat_session SET updated_at = ? WHERE chat_session_id = ?", DB_NOW.plusSeconds(1), ab);
        send(get(CHATS), A).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].chat_session_id").value(ab.toString()));
    }

    @Test
    void getAndListOnlyExposeMetadataAndNeverWrite() throws Exception {
        friendship(A, B);
        UUID sessionId = open(A, B);
        addHistory(sessionId);
        List<String> before = fingerprints();
        JsonNode detail = body(send(get(DETAIL + sessionId), B).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")));
        JsonNode list = body(send(get(CHATS), B).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")));
        for (JsonNode resource : List.of(detail, list.path("items").get(0))) {
            assertThat(fields(resource)).containsExactlyInAnyOrder("chat_session_id", "friend_account_id", "created_at", "updated_at");
            assertThat(resource.toString()).doesNotContain("private-test/", "retained-message", "storage_object_key", "sender_account_id", "last_sequence_number");
            assertThat(resource.path("friend_account_id").asText()).isEqualTo(A.toString());
        }
        assertThat(fingerprints()).isEqualTo(before);
    }

    @Test
    void sessionWithMissingParticipantIsHiddenAndIsNotAutomaticallyRepaired() throws Exception {
        friendship(A, B);
        UUID sessionId = open(A, B);
        jdbc.update("DELETE FROM public.chat_participant WHERE chat_session_id = ? AND account_id = ?", sessionId, B);
        List<String> before = fingerprints();
        for (UUID actor : List.of(A, B)) {
            send(get(DETAIL + sessionId), actor).andExpect(status().isNotFound());
            send(get(CHATS), actor).andExpect(status().isOk()).andExpect(jsonPath("$.total_elements").value(0));
        }
        send(openRequest(B), A).andExpect(status().isNotFound());
        assertThat(fingerprints()).isEqualTo(before);
    }

    @Test
    void sessionWithWrongParticipantIsHiddenEvenFromInsertedOutsider() throws Exception {
        friendship(A, B);
        UUID sessionId = open(A, B);
        jdbc.update("UPDATE public.chat_participant SET account_id = ? WHERE chat_session_id = ? AND account_id = ?", C, sessionId, B);
        List<String> before = fingerprints();
        for (UUID actor : List.of(A, B, C)) {
            send(get(DETAIL + sessionId), actor).andExpect(status().isNotFound());
            send(get(CHATS), actor).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        }
        send(openRequest(B), A).andExpect(status().isNotFound());
        assertThat(fingerprints()).isEqualTo(before);
    }

    @Test
    void unfriendHidesBothListsAndBlocksOpenDetailThenRefriendRestoresSameSessionAndHistory() throws Exception {
        UUID pair = friendship(A, B);
        UUID sessionId = open(A, B);
        addHistory(sessionId);
        List<String> chatBefore = chatFingerprints();
        send(delete("/api/v1/me/friends/" + B), A).andExpect(status().isNoContent());
        for (UUID actor : List.of(A, B)) {
            send(get(CHATS), actor).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
            send(get(DETAIL + sessionId), actor).andExpect(status().isNotFound());
        }
        send(openRequest(B), A).andExpect(status().isNotFound());
        send(openRequest(A), B).andExpect(status().isNotFound());
        assertThat(chatFingerprints()).isEqualTo(chatBefore);

        // Persistence fixture simulates a later valid re-friend. No invitation API is faked here.
        jdbc.update("UPDATE public.friendship SET unfriended_at = NULL, established_at = ?, updated_at = ? WHERE friendship_id = ?",
                DB_NOW.plusSeconds(1), DB_NOW.plusSeconds(1), pair);
        assertThat(id(body(send(openRequest(A), B).andExpect(status().isOk())))).isEqualTo(sessionId);
        for (UUID actor : List.of(A, B)) {
            send(get(CHATS), actor).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].chat_session_id").value(sessionId.toString()));
            send(get(DETAIL + sessionId), actor).andExpect(status().isOk());
        }
        assertThat(chatFingerprints()).isEqualTo(chatBefore);
    }

    @Test
    void failureAfterWritingOneParticipantRollsBackEntireNewConversation() throws Exception {
        friendship(A, B);
        doAnswer(invocation -> {
            Iterable<ChatParticipant> rows = invocation.getArgument(0);
            ChatParticipant first = rows.iterator().next();
            jdbc.update("""
                    INSERT INTO public.chat_participant
                      (chat_participant_id, chat_session_id, account_id, participant_slot, joined_at)
                    VALUES (?, ?, ?, ?, ?)
                    """, first.getChatParticipantId(), first.getChatSessionId(), first.getAccountId(),
                    first.getParticipantSlot(), first.getJoinedAt());
            throw new IllegalStateException("Simulated persistence fault after first participant");
        }).when(participants).saveAllAndFlush(any());

        send(openRequest(B), A).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        assertThat(count("chat_session")).isZero();
        assertThat(count("chat_participant")).isZero();
        assertThat(count("friendship")).isEqualTo(1);
    }

    @Test
    void concurrentReverseOpensOverRealHttpCreateOneConversationAndExactlyTwoMembers() throws Exception {
        friendship(A, B);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> concurrentOpen(A, B, ready, start));
            var second = workers.submit(() -> concurrentOpen(B, A, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var one = first.get(15, TimeUnit.SECONDS);
            var two = second.get(15, TimeUnit.SECONDS);
            assertThat(List.of(one.getStatusCode().value(), two.getStatusCode().value())).containsExactlyInAnyOrder(201, 200);
            assertThat(id(mapper.readTree(one.getBody()))).isEqualTo(id(mapper.readTree(two.getBody())));
        } finally {
            start.countDown();
        }
        assertThat(count("chat_session")).isEqualTo(1);
        assertThat(count("chat_participant")).isEqualTo(2);
    }

    @Test
    void openWaitingForUnfriendLockRechecksCommittedInactiveStateAndCreatesNothing() throws Exception {
        friendship(A, B);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var unfriend = workers.submit(() -> transaction.executeWithoutResult(status -> {
                var pair = friendships.findLockedByAccountLowIdAndAccountHighId(A, B).orElseThrow();
                pair.setUnfriendedAt(DB_NOW);
                pair.setUpdatedAt(DB_NOW);
                friendships.saveAndFlush(pair);
                locked.countDown();
                await(release);
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var opening = workers.submit(() -> http.exchange(CHATS, HttpMethod.POST, authenticated(A, payload(B)), String.class));
            try {
                waitForBlockedFriendshipQuery();
                assertThatThrownBy(() -> opening.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally {
                release.countDown();
            }
            unfriend.get(10, TimeUnit.SECONDS);
            assertThat(opening.get(10, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        } finally {
            release.countDown();
        }
        assertThat(count("chat_session")).isZero();
        assertThat(count("chat_participant")).isZero();
    }

    @Test
    void realHttpExercisesCreateReuseDetailListAndCompletedUnfriendGuards() throws Exception {
        friendship(A, B);
        var created = http.exchange(CHATS, HttpMethod.POST, authenticated(A, payload(B)), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID sessionId = id(mapper.readTree(created.getBody()));
        assertThat(http.exchange(CHATS, HttpMethod.POST, authenticated(B, payload(A)), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.exchange(DETAIL + sessionId, HttpMethod.GET, authenticated(A, null), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        var listed = http.exchange(CHATS, HttpMethod.GET, authenticated(B, null), String.class);
        assertThat(id(mapper.readTree(listed.getBody()).path("items").get(0))).isEqualTo(sessionId);
        assertThat(http.exchange(DETAIL + sessionId, HttpMethod.GET, authenticated(C, null), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(http.exchange("/api/v1/me/friends/" + B, HttpMethod.DELETE, authenticated(A, null), String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(http.exchange(DETAIL + sessionId, HttpMethod.GET, authenticated(B, null), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(mapper.readTree(http.exchange(CHATS, HttpMethod.GET, authenticated(A, null), String.class).getBody()).path("items")).isEmpty();
    }

    @Test
    void openApiDocumentsBothOpenStatusesLocationSafeDtosPagingAndBearerAuth() throws Exception {
        JsonNode document = body(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));
        JsonNode paths = document.path("paths");
        JsonNode open = paths.path(CHATS).path("post");
        assertThat(open.path("responses").has("201")).isTrue();
        assertThat(open.path("responses").has("200")).isTrue();
        assertThat(open.path("responses").path("201").path("headers").has("Location")).isTrue();
        assertThat(open.path("requestBody").path("content").path("application/json").path("schema").path("$ref").asText())
                .endsWith("/OpenChatRequest");
        assertThat(paths.path(CHATS).path("get").path("parameters")).hasSize(2);
        assertThat(paths.path("/api/v1/chats/{chatSessionId}").path("get").path("responses").has("404")).isTrue();
        assertThat(document.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
        assertThat(open.path("security").toString()).contains("bearerAuth");
        assertThat(fields(document.at("/components/schemas/ChatSessionResponse/properties")))
                .containsExactlyInAnyOrder("chat_session_id", "friend_account_id", "created_at", "updated_at");
    }

    @Test
    void sessionApisDoNotExposeCloudUploadPlaceholderEndpoints() throws Exception {
        friendship(A, B);
        UUID sessionId = open(A, B);
        send(post(DETAIL + sessionId + "/attachments").contentType(MediaType.APPLICATION_JSON).content("{}"), A)
                .andExpect(status().isNotFound());
        send(get(DETAIL + sessionId + "/attachments"), A).andExpect(status().isNotFound());
        assertThat(count("chat_attachment")).isZero();
    }

    private List<Supplier<MockHttpServletRequestBuilder>> routes() {
        return List.of(() -> openRequest(B), () -> get(CHATS), () -> get(DETAIL + UUID.randomUUID()));
    }

    private UUID friendship(UUID first, UUID second) {
        FriendshipPair pair = new FriendshipPair(first, second);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public.friendship (friendship_id, account_low_id, account_high_id, established_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, pair.accountLowId(), pair.accountHighId(), EARLIER, EARLIER, EARLIER);
        return id;
    }

    private UUID open(UUID actor, UUID peer) throws Exception {
        return id(body(send(openRequest(peer), actor).andExpect(status().isCreated())));
    }

    private MockHttpServletRequestBuilder openRequest(UUID peer) {
        return post(CHATS).contentType(MediaType.APPLICATION_JSON).content(payload(peer));
    }

    private String payload(UUID peer) {
        return "{\"friend_account_id\":\"" + peer + "\"}";
    }

    private ResultActions send(MockHttpServletRequestBuilder request, UUID actor) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION,
                "Bearer " + token(actor.toString(), List.of("CUSTOMER"), Instant.now().plusSeconds(600))));
    }

    private HttpEntity<String> authenticated(UUID actor, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token(actor.toString(), List.of("CUSTOMER"), Instant.now().plusSeconds(600)));
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return new HttpEntity<>(body, headers);
    }

    private org.springframework.http.ResponseEntity<String> concurrentOpen(
            UUID actor, UUID peer, CountDownLatch ready, CountDownLatch start) throws Exception {
        HttpEntity<String> request = authenticated(actor, payload(peer));
        ready.countDown();
        await(start);
        return http.exchange(CHATS, HttpMethod.POST, request, String.class);
    }

    private void addHistory(UUID sessionId) {
        UUID messageId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public.chat_message
                  (chat_message_id, chat_session_id, sender_account_id, client_message_id, sequence_number, content, created_at)
                VALUES (?, ?, ?, ?, 1, 'retained-message', ?)
                """, messageId, sessionId, A, UUID.randomUUID(), DB_NOW);
        jdbc.update("""
                INSERT INTO public.chat_attachment
                  (chat_attachment_id, chat_message_id, display_order, storage_object_key, content_type, size_bytes, created_at)
                VALUES (?, ?, 1, ?, 'image/png', 100, ?)
                """, UUID.randomUUID(), messageId, "private-test/" + UUID.randomUUID(), DB_NOW);
        jdbc.update("UPDATE public.chat_session SET last_sequence_number = 1 WHERE chat_session_id = ?", sessionId);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM public." + table, Integer.class);
    }

    private List<String> fingerprints() {
        List<String> rows = new ArrayList<>(chatFingerprints());
        rows.addAll(jdbc.queryForList("SELECT md5(row_to_json(f)::text) FROM public.friendship f ORDER BY friendship_id", String.class));
        return rows;
    }

    private List<String> chatFingerprints() {
        return jdbc.queryForList("""
                SELECT 'session:' || md5(row_to_json(s)::text) FROM public.chat_session s
                UNION ALL SELECT 'participant:' || md5(row_to_json(p)::text) FROM public.chat_participant p
                UNION ALL SELECT 'message:' || md5(row_to_json(m)::text) FROM public.chat_message m
                UNION ALL SELECT 'image:' || md5(row_to_json(a)::text) FROM public.chat_attachment a ORDER BY 1
                """, String.class);
    }

    private JsonNode body(ResultActions response) throws Exception {
        return mapper.readTree(response.andReturn().getResponse().getContentAsByteArray());
    }

    private UUID id(JsonNode resource) {
        return UUID.fromString(resource.path("chat_session_id").asText());
    }

    private List<String> fields(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private void waitForBlockedFriendshipQuery() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            int waiting = jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity
                    WHERE datname = current_database() AND wait_event_type = 'Lock' AND query LIKE '%friendship%'
                    """, Integer.class);
            if (waiting > 0) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Expected an HTTP request blocked on the Friendship row lock");
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test synchronization timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Test synchronization interrupted", exception);
        }
    }

    private String token(String subject, List<String> roles, Instant expiresAt) {
        var claims = JwtClaimsSet.builder().subject(subject).issuedAt(Instant.now().minusSeconds(300))
                .expiresAt(expiresAt).claim("roles", roles).build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY.getKeyID()).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(KEY)))
                .encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
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
        JwtDecoder chatSessionTestJwtDecoder() throws Exception {
            return NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build();
        }
    }
}

