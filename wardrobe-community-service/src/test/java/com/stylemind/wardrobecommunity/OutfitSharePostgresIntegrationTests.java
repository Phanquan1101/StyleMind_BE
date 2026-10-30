package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.stylemind.wardrobecommunity.client.commerce.CommerceCatalogClient;
import com.stylemind.wardrobecommunity.client.commerce.payload.VariantLookupItem;
import com.stylemind.wardrobecommunity.client.identity.IdentityDirectoryClient;
import com.stylemind.wardrobecommunity.client.identity.payload.AccountLookupItem;
import com.stylemind.wardrobecommunity.client.notification.ShareInvitationMailClient;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.repository.outfit.OutfitRepository;
import com.stylemind.wardrobecommunity.service.friendship.FriendshipService;
import com.stylemind.wardrobecommunity.service.outfit.OutfitShareService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

@SpringBootTest(properties = "management.health.db.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
@Import(OutfitSharePostgresIntegrationTests.JwtTestConfiguration.class)
class OutfitSharePostgresIntegrationTests {

    private static final String OUTFITS = "/api/v1/me/outfits";
    private static final String SHARED = "/api/v1/me/shared-outfits";
    private static final String PUBLIC = "/api/v1/public/outfits";
    private static final RSAKey SIGNING_KEY = generateRsaKey();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18.6")
                    .withDatabaseName("stylemind_wardrobe_community_share_test")
                    .withUsername("wardrobe_community_share_test")
                    .withPassword("test-only-not-a-secret");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private FriendshipService friendshipService;
    @Autowired private OutfitShareService sharing;
    @Autowired private OutfitRepository outfits;
    @Autowired private TransactionTemplate transaction;
    @MockitoBean private CommerceCatalogClient catalog;
    @MockitoBean private IdentityDirectoryClient identity;
    @MockitoBean private ShareInvitationMailClient mail;

    private final Map<UUID, VariantLookupItem> commerce = new HashMap<>();
    private final Map<UUID, AccountLookupItem> accounts = new HashMap<>();
    private UUID owner;
    private UUID viewer;
    private UUID stranger;
    private UUID variant;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM public.outfit_share");
        jdbc.update("DELETE FROM public.outfit_item");
        jdbc.update("DELETE FROM public.outfit");
        jdbc.update("DELETE FROM public.friendship");
        commerce.clear();
        accounts.clear();
        owner = account("Owner@Example.Test", "Linh Tran");
        viewer = account("viewer@example.test", "Minh Le");
        stranger = account("stranger@example.test", "Sam Pham");
        variant = variant();
        when(catalog.lookup(any()))
                .thenAnswer(
                        invocation -> {
                            Collection<UUID> ids = invocation.getArgument(0);
                            Map<UUID, VariantLookupItem> found = new LinkedHashMap<>();
                            ids.forEach(
                                    id -> {
                                        if (commerce.containsKey(id)) {
                                            found.put(id, commerce.get(id));
                                        }
                                    });
                            return found;
                        });
        when(identity.lookup(any()))
                .thenAnswer(
                        invocation -> {
                            Collection<UUID> ids = invocation.getArgument(0);
                            Map<UUID, AccountLookupItem> found = new LinkedHashMap<>();
                            ids.forEach(
                                    id -> {
                                        if (accounts.containsKey(id)) {
                                            found.put(id, accounts.get(id));
                                        }
                                    });
                            return found;
                        });
    }

    // The approved cutover replaces the old email-grant success scenarios with 410
    // assertions and current-friend visibility. Original baseline is snapshotted in evidence.
    @Test
    void authAndRolesProtectAllOwnerAndFriendRoutes() throws Exception {
        UUID id = seedOutfit(owner, "Look", OffsetDateTime.now(), variant);
        for (String jwt : new String[] {null, token(stranger, "ADMIN")}) {
            int expected = jwt == null ? 401 : 403;
            for (var route :
                    List.of(
                            get(settings(id)),
                            patch(settings(id)),
                            get(friendList(owner)),
                            get(friendDetail(owner, id)),
                            post(OUTFITS + "/" + id + "/shares"),
                            get(OUTFITS + "/" + id + "/shares"),
                            delete(OUTFITS + "/" + id + "/shares/" + UUID.randomUUID()),
                            get(SHARED),
                            get(SHARED + "/" + id),
                            put(OUTFITS + "/" + id + "/public-link"),
                            delete(OUTFITS + "/" + id + "/public-link")))
                send(route, jwt, "{\"visibility\":\"FRIENDS\",\"email\":\"viewer@example.test\"}")
                        .andExpect(status().is(expected));
        }
        send(get(PUBLIC + "/unknown-fixture"), null, null).andExpect(status().isNotFound());
        send(post(PUBLIC + "/unknown-fixture"), null, null).andExpect(status().isUnauthorized());
    }

    @Test
    void defaultPrivateAndSettingsAreOwnerOnlyWithStrictBody() throws Exception {
        UUID id = seedOutfit(owner, "Look", OffsetDateTime.now(), variant);
        var initial =
                json(
                        send(get(settings(id)), token(owner, "CUSTOMER"), null)
                                .andExpect(status().isOk())
                                .andExpect(header().string("Cache-Control", "no-store")));
        assertThat(initial.path("visibility").asText()).isEqualTo("PRIVATE");
        assertThat(initial.path("public_enabled").asBoolean()).isFalse();
        for (String body :
                List.of(
                        "{}",
                        "null",
                        "[]",
                        "{\"visibility\":null}",
                        "{\"visibility\":\"friends\"}",
                        "{\"visibility\":\"PUBLIC\",\"owner_account_id\":\"other\"}",
                        "{\"visibility\":\"PRIVATE\",\"visibility\":\"PUBLIC\"}",
                        "{\"visibility\":\"PUBLIC\"} {}"))
            assertProblem(
                    send(patch(settings(id)), token(owner, "CUSTOMER"), body),
                    400,
                    "INVALID_REQUEST");
        send(
                        patch(settings(id)).contentType(MediaType.APPLICATION_JSON),
                        token(owner, "CUSTOMER"),
                        null)
                .andExpect(status().isBadRequest());
        for (UUID actor : List.of(viewer, stranger)) {
            assertProblem(
                    send(get(settings(id)), token(actor, "CUSTOMER"), null),
                    404,
                    "RESOURCE_NOT_FOUND");
            assertProblem(
                    send(
                            patch(settings(id)),
                            token(actor, "CUSTOMER"),
                            "{\"visibility\":\"PUBLIC\"}"),
                    404,
                    "RESOURCE_NOT_FOUND");
        }
        assertProblem(
                send(get(OUTFITS + "/bad-uuid/sharing"), token(owner, "CUSTOMER"), null),
                400,
                "INVALID_REQUEST");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT visibility FROM outfit WHERE outfit_id=?",
                                String.class,
                                id))
                .isEqualTo("PRIVATE");
    }

    @Test
    void legacyEmailGrantsAndFeedAreRetiredWithoutMailOrMutations() throws Exception {
        UUID id = seedOutfit(owner, "Private", OffsetDateTime.now(), variant);
        UUID grant = seedShare(id, "viewer@example.test", OffsetDateTime.now(), null);
        for (var route :
                List.of(
                        get(OUTFITS + "/" + id + "/shares"),
                        post(OUTFITS + "/" + id + "/shares"),
                        delete(OUTFITS + "/" + id + "/shares/" + grant))) {
            assertProblem(
                    send(route, token(owner, "CUSTOMER"), "{\"email\":\"viewer@example.test\"}"),
                    410,
                    "OUTFIT_EMAIL_SHARING_RETIRED");
        }
        assertProblem(
                send(get(SHARED), token(viewer, "CUSTOMER"), null),
                410,
                "OUTFIT_EMAIL_SHARING_RETIRED");
        assertProblem(
                send(get(SHARED + "/" + id), token(viewer, "CUSTOMER"), null),
                410,
                "OUTFIT_EMAIL_SHARING_RETIRED");
        assertProblem(
                send(get(OUTFITS + "/" + id + "/shares"), token(stranger, "CUSTOMER"), null),
                404,
                "RESOURCE_NOT_FOUND");
        pair(owner, viewer);
        assertProblem(
                send(get(friendDetail(owner, id)), token(viewer, "CUSTOMER"), null),
                404,
                "RESOURCE_NOT_FOUND");
        assertThat(count("outfit_share")).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT revoked_at FROM outfit_share WHERE outfit_share_id=?",
                                OffsetDateTime.class,
                                grant))
                .isNull();
        org.mockito.Mockito.verifyNoInteractions(mail);
    }

    @Test
    void friendsModePermitsAllCurrentFriendsButNeverPrivateOrAnotherParent() throws Exception {
        UUID secondFriend = account("another@example.test", "Another Friend");
        pair(owner, viewer);
        pair(owner, secondFriend);
        UUID visible = seedOutfit(owner, "Friends look", OffsetDateTime.now(), variant);
        UUID hidden = seedOutfit(owner, "Private look", OffsetDateTime.now(), variant);
        mode(visible, "FRIENDS");
        for (UUID actor : List.of(viewer, secondFriend)) {
            var list =
                    json(
                            send(get(friendList(owner)), token(actor, "CUSTOMER"), null)
                                    .andExpect(status().isOk()));
            assertThat(list.path("total_elements").asInt()).isEqualTo(1);
            assertThat(list.path("items").get(0).path("outfit_id").asText())
                    .isEqualTo(visible.toString());
            var detail =
                    json(
                            send(get(friendDetail(owner, visible)), token(actor, "CUSTOMER"), null)
                                    .andExpect(status().isOk()));
            assertThat(detail.path("items").get(0).path("variant_id").asText())
                    .isEqualTo(variant.toString());
            assertThat(detail.has("public_token")).isFalse();
            assertThat(detail.has("owner_account_id")).isFalse();
            assertThat(detail.has("email")).isFalse();
            assertProblem(
                    send(get(friendDetail(owner, hidden)), token(actor, "CUSTOMER"), null),
                    404,
                    "RESOURCE_NOT_FOUND");
        }
        assertProblem(
                send(get(friendList(owner)), token(stranger, "CUSTOMER"), null),
                404,
                "RESOURCE_NOT_FOUND");
        assertProblem(
                send(get(friendList(owner)), token(owner, "CUSTOMER"), null),
                404,
                "RESOURCE_NOT_FOUND");
        pair(viewer, stranger);
        assertProblem(
                send(get(friendDetail(stranger, visible)), token(viewer, "CUSTOMER"), null),
                404,
                "RESOURCE_NOT_FOUND");
        assertThat(count("outfit_share")).isZero();
        org.mockito.Mockito.verifyNoInteractions(mail);
    }

    @Test
    void privateNeverLeaksThroughCountsOrStablePaginationAndListNeverCallsCommerce()
            throws Exception {
        pair(owner, viewer);
        OffsetDateTime time = OffsetDateTime.now().minusDays(1);
        for (int i = 0; i < 3; i++) {
            UUID id = seedOutfit(owner, "Visible " + i, time, variant);
            mode(id, i == 1 ? "PUBLIC" : "FRIENDS");
        }
        seedOutfit(owner, "Secret", OffsetDateTime.now(), variant);
        // Fix equal timestamps to exercise the UUID tie break without depending on write clock.
        jdbc.update("UPDATE outfit SET updated_at=? WHERE visibility<>'PRIVATE'", time);
        var expected =
                jdbc.queryForList(
                        "SELECT outfit_id::text FROM outfit WHERE visibility<>'PRIVATE' ORDER BY"
                            + " updated_at DESC,outfit_id DESC",
                        String.class);
        for (int page = 0; page < 3; page++) {
            var result =
                    json(
                            send(
                                    get(friendList(owner))
                                            .param("page", String.valueOf(page))
                                            .param("size", "1"),
                                    token(viewer, "CUSTOMER"),
                                    null));
            assertThat(result.path("total_elements").asInt()).isEqualTo(3);
            assertThat(result.path("total_pages").asInt()).isEqualTo(3);
            assertThat(result.path("items").get(0).path("outfit_id").asText())
                    .isEqualTo(expected.get(page));
        }
        var beyond =
                json(
                        send(
                                get(friendList(owner))
                                        .param("page", "2147483647")
                                        .param("size", "100"),
                                token(viewer, "CUSTOMER"),
                                null));
        assertThat(beyond.path("items")).isEmpty();
        assertThat(beyond.path("total_elements").asInt()).isEqualTo(3);
        for (var route :
                List.of(
                        get(friendList(owner)).param("page", "-1"),
                        get(friendList(owner)).param("size", "101"),
                        get(friendList(owner)).param("size", "0"),
                        get(friendList(owner)).param("page", "0", "1"),
                        get(friendList(owner)).param("sort", "name")))
            assertProblem(send(route, token(viewer, "CUSTOMER"), null), 400, "INVALID_REQUEST");
        verify(catalog, never()).lookup(any());
    }

    @Test
    void publicLinkSurvivesOffOnAndAliasesDoNotChangeFriendsOrNoopTimestamps() throws Exception {
        UUID id = seedOutfit(owner, "Public look", OffsetDateTime.now().minusDays(1), variant);
        var published = mode(id, "PUBLIC");
        String link = published.path("public_token").asText();
        assertThat(link).hasSize(43);
        Object updated =
                jdbc.queryForObject(
                        "SELECT updated_at FROM outfit WHERE outfit_id=?",
                        OffsetDateTime.class,
                        id);
        var again = mode(id, "PUBLIC");
        assertThat(again).isEqualTo(published);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT updated_at FROM outfit WHERE outfit_id=?",
                                OffsetDateTime.class,
                                id))
                .isEqualTo(updated);
        var guest =
                json(
                        send(get(PUBLIC + "/" + link), null, null)
                                .andExpect(status().isOk())
                                .andExpect(header().string("Cache-Control", "no-store")));
        assertThat(guest.has("public_token")).isFalse();
        assertThat(guest.has("owner_account_id")).isFalse();
        mode(id, "FRIENDS");
        send(delete(OUTFITS + "/" + id + "/public-link"), token(owner, "CUSTOMER"), null)
                .andExpect(status().isNoContent());
        var inactive = json(send(get(settings(id)), token(owner, "CUSTOMER"), null));
        assertThat(inactive.path("visibility").asText()).isEqualTo("FRIENDS");
        assertThat(inactive.path("public_token").asText()).isEqualTo(link);
        var denied = send(get(PUBLIC + "/" + link), null, null);
        assertProblem(denied, 404, "RESOURCE_NOT_FOUND");
        assertThat(json(denied).path("instance").asText())
                .isEqualTo(PUBLIC); // No credential in error metadata.
        var alias =
                json(
                        send(
                                        put(OUTFITS + "/" + id + "/public-link"),
                                        token(owner, "CUSTOMER"),
                                        null)
                                .andExpect(status().isOk()));
        assertThat(alias.path("public_token").asText()).isEqualTo(link);
        send(delete(OUTFITS + "/" + id + "/public-link"), token(owner, "CUSTOMER"), null)
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"));
        assertThat(
                        json(send(get(settings(id)), token(owner, "CUSTOMER"), null))
                                .path("visibility")
                                .asText())
                .isEqualTo("PRIVATE");
        assertProblem(send(get(PUBLIC + "/" + link), null, null), 404, "RESOURCE_NOT_FOUND");
        assertThat(mode(id, "PUBLIC").path("public_token").asText()).isEqualTo(link);
    }

    @Test
    void viewersCannotMutateOwnerResourcesAndDeleteInvalidatesEveryReadPath() throws Exception {
        pair(owner, viewer);
        UUID id = seedOutfit(owner, "Look", OffsetDateTime.now(), variant);
        String link = mode(id, "PUBLIC").path("public_token").asText();
        UUID item =
                jdbc.queryForObject(
                        "SELECT outfit_item_id FROM outfit_item WHERE outfit_id=?", UUID.class, id);
        for (var route :
                List.of(
                        patch(OUTFITS + "/" + id),
                        post(OUTFITS + "/" + id + "/items"),
                        patch(OUTFITS + "/" + id + "/items/" + item),
                        delete(OUTFITS + "/" + id + "/items/" + item),
                        delete(OUTFITS + "/" + id),
                        put(OUTFITS + "/" + id + "/public-link"),
                        delete(OUTFITS + "/" + id + "/public-link"))) {
            String body = mapper.writeValueAsString(Map.of("name", "Changed"));
            // Each route receives the valid payload for that operation so validation cannot mask
            // ownership.
            String uri =
                    route.buildRequest(new org.springframework.mock.web.MockServletContext())
                            .getRequestURI();
            if (uri.contains("/items"))
                body = mapper.writeValueAsString(Map.of("variant_id", variant.toString()));
            send(route, token(viewer, "CUSTOMER"), body).andExpect(status().isNotFound());
        }
        send(delete(OUTFITS + "/" + id), token(owner, "CUSTOMER"), null)
                .andExpect(status().isNoContent());
        assertProblem(send(get(PUBLIC + "/" + link), null, null), 404, "RESOURCE_NOT_FOUND");
        assertProblem(
                send(get(friendDetail(owner, id)), token(viewer, "CUSTOMER"), null),
                404,
                "RESOURCE_NOT_FOUND");
    }

    @Test
    void unfriendBlocksBothDirectionsAndRefriendRestoresOnlyVisibleOutfits() throws Exception {
        UUID pair = pair(owner, viewer);
        UUID id = seedOutfit(owner, "Friends", OffsetDateTime.now(), variant);
        mode(id, "FRIENDS");
        UUID hidden = seedOutfit(owner, "Private", OffsetDateTime.now(), variant);
        friendshipService.unfriend(owner, viewer);
        assertProblem(
                send(get(friendList(owner)), token(viewer, "CUSTOMER"), null),
                404,
                "RESOURCE_NOT_FOUND");
        assertProblem(
                send(get(friendList(viewer)), token(owner, "CUSTOMER"), null),
                404,
                "RESOURCE_NOT_FOUND");
        jdbc.update(
                "UPDATE friendship SET unfriended_at=NULL,established_at=updated_at WHERE"
                    + " friendship_id=?",
                pair);
        send(get(friendDetail(owner, id)), token(viewer, "CUSTOMER"), null)
                .andExpect(status().isOk());
        assertProblem(
                send(get(friendDetail(owner, hidden)), token(viewer, "CUSTOMER"), null),
                404,
                "RESOURCE_NOT_FOUND");
    }

    @Test
    void rechecksVisibilityAfterCatalogIoWithoutKeepingDatabaseTransactionOpen() throws Exception {
        pair(owner, viewer);
        UUID id = seedOutfit(owner, "Friends", OffsetDateTime.now(), variant);
        mode(id, "FRIENDS");
        org.mockito.Mockito.doAnswer(
                        call -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            sharing.changeVisibility(owner, id, "PRIVATE");
                            return Map.copyOf(commerce);
                        })
                .when(catalog)
                .lookup(any());
        assertProblem(
                send(get(friendDetail(owner, id)), token(viewer, "CUSTOMER"), null),
                404,
                "RESOURCE_NOT_FOUND");
    }

    @Test
    void rechecksUnfriendAfterCatalogIoAndFriendshipAfterListIdentityIo() throws Exception {
        pair(owner, viewer);
        UUID id = seedOutfit(owner, "Friends", OffsetDateTime.now(), variant);
        mode(id, "FRIENDS");
        org.mockito.Mockito.doAnswer(
                        call -> {
                            friendshipService.unfriend(owner, viewer);
                            return Map.copyOf(commerce);
                        })
                .when(catalog)
                .lookup(any());
        assertProblem(
                send(get(friendDetail(owner, id)), token(viewer, "CUSTOMER"), null),
                404,
                "RESOURCE_NOT_FOUND");
        jdbc.update("UPDATE friendship SET unfriended_at=NULL,established_at=updated_at");
        org.mockito.Mockito.doAnswer(
                        call -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            friendshipService.unfriend(owner, viewer);
                            return Map.copyOf(accounts);
                        })
                .when(identity)
                .lookup(any());
        assertProblem(
                send(get(friendList(owner)), token(viewer, "CUSTOMER"), null),
                404,
                "RESOURCE_NOT_FOUND");
    }

    @Test
    void publicReaderRechecksAfterIoAndKeepsUnavailableItems() throws Exception {
        UUID id = seedOutfit(owner, "Public", OffsetDateTime.now(), variant);
        String link = mode(id, "PUBLIC").path("public_token").asText();
        commerce.clear();
        var unavailable =
                json(send(get(PUBLIC + "/" + link), null, null).andExpect(status().isOk()));
        assertThat(unavailable.path("items")).hasSize(1);
        assertThat(unavailable.path("items").get(0).path("available").asBoolean()).isFalse();
        org.mockito.Mockito.doAnswer(
                        call -> {
                            sharing.changeVisibility(owner, id, "PRIVATE");
                            return Map.of();
                        })
                .when(catalog)
                .lookup(any());
        assertProblem(send(get(PUBLIC + "/" + link), null, null), 404, "RESOURCE_NOT_FOUND");
    }

    @Test
    void downstreamFailureDoesNotBypassModeOrLeakPrivateMetadata() throws Exception {
        pair(owner, viewer);
        UUID id = seedOutfit(owner, "Hidden", OffsetDateTime.now(), variant);
        doThrow(
                        new ApiOperationException(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                "CATALOG_UNAVAILABLE",
                                "Catalog is unavailable"))
                .when(catalog)
                .lookup(any());
        assertProblem(
                send(get(friendDetail(owner, id)), token(viewer, "CUSTOMER"), null),
                404,
                "RESOURCE_NOT_FOUND");
        verify(catalog, never()).lookup(any());
        mode(id, "FRIENDS");
        send(get(friendDetail(owner, id)), token(viewer, "CUSTOMER"), null)
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void writeWaitsForOwnedRowLockAndTransactionRollbackPreservesLinkState() throws Exception {
        UUID id = seedOutfit(owner, "Concurrent", OffsetDateTime.now(), variant);
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder =
                    pool.submit(
                            () ->
                                    transaction.executeWithoutResult(
                                            tx -> {
                                                outfits.findLockedByOutfitIdAndOwnerAccountId(
                                                                id, owner)
                                                        .orElseThrow();
                                                locked.countDown();
                                                try {
                                                    if (!release.await(10, TimeUnit.SECONDS))
                                                        throw new IllegalStateException(
                                                                "Test lock timeout");
                                                } catch (InterruptedException e) {
                                                    Thread.currentThread().interrupt();
                                                    throw new IllegalStateException(e);
                                                }
                                            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            CountDownLatch started = new CountDownLatch(1);
            Future<?> writer =
                    pool.submit(
                            () -> {
                                started.countDown();
                                return sharing.changeVisibility(owner, id, "PUBLIC");
                            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> writer.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            writer.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
        var before = sharing.settings(owner, id);
        assertThatThrownBy(
                        () ->
                                transaction.executeWithoutResult(
                                        tx -> {
                                            sharing.changeVisibility(owner, id, "FRIENDS");
                                            throw new IllegalStateException("fixture rollback");
                                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(sharing.settings(owner, id)).isEqualTo(before);
        var executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Void>> calls = new ArrayList<>();
            for (String mode : List.of("PRIVATE", "PUBLIC", "FRIENDS", "PUBLIC"))
                calls.add(
                        () -> {
                            sharing.changeVisibility(owner, id, mode);
                            return null;
                        });
            for (Future<Void> result : executor.invokeAll(calls)) result.get(10, TimeUnit.SECONDS);
            var after = sharing.settings(owner, id);
            assertThat(after.publicToken()).isEqualTo(before.publicToken());
            assertThat(after.publicEnabled()).isEqualTo("PUBLIC".equals(after.visibility()));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void swaggerExposesNewPathsAndDeprecatesLegacyEmailSharing() throws Exception {
        var spec = json(send(get("/v3/api-docs"), null, null).andExpect(status().isOk()));
        assertThat(spec.path("paths").has(OUTFITS + "/{outfitId}/sharing")).isTrue();
        assertThat(spec.path("paths").has("/api/v1/me/friends/{friendAccountId}/outfits")).isTrue();
        assertThat(
                        spec.path("paths")
                                .path(OUTFITS + "/{outfitId}/shares")
                                .path("post")
                                .path("deprecated")
                                .asBoolean())
                .isTrue();
        assertThat(
                        spec.path("paths")
                                .path(OUTFITS + "/{outfitId}/shares")
                                .path("post")
                                .path("responses")
                                .has("410"))
                .isTrue();
        var values =
                spec.path("components")
                        .path("schemas")
                        .path("OutfitVisibilityRequest")
                        .path("properties")
                        .path("visibility")
                        .path("enum");
        assertThat(values.isArray()).isTrue();
        assertThat(values.toString()).contains("PRIVATE", "FRIENDS", "PUBLIC");
    }

    private String settings(UUID id) {
        return OUTFITS + "/" + id + "/sharing";
    }

    private String friendList(UUID peer) {
        return "/api/v1/me/friends/" + peer + "/outfits";
    }

    private String friendDetail(UUID peer, UUID id) {
        return friendList(peer) + "/" + id;
    }

    private JsonNode mode(UUID id, String mode) throws Exception {
        return json(
                send(
                                patch(settings(id)),
                                token(owner, "CUSTOMER"),
                                mapper.writeValueAsString(Map.of("visibility", mode)))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Cache-Control", "no-store")));
    }

    private UUID pair(UUID a, UUID b) {
        UUID low = a.toString().compareTo(b.toString()) < 0 ? a : b,
                high = low.equals(a) ? b : a,
                id = UUID.randomUUID();
        OffsetDateTime time = OffsetDateTime.now().minusDays(1);
        jdbc.update(
                "INSERT INTO"
                    + " friendship(friendship_id,account_low_id,account_high_id,established_at,created_at,updated_at)"
                    + " VALUES (?,?,?,?,?,?)",
                id,
                low,
                high,
                time,
                time,
                time);
        return id;
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions shareWith(UUID ownerId, UUID outfitId, String email) throws Exception {
        return send(
                post(OUTFITS + "/" + outfitId + "/shares"),
                token(ownerId, "CUSTOMER"),
                mapper.writeValueAsString(Map.of("email", email)));
    }

    private UUID account(String email, String displayName) {
        UUID id = UUID.randomUUID();
        accounts.put(id, new AccountLookupItem(id, email, displayName, "ACTIVE"));
        return id;
    }

    private UUID variant() {
        return variantNamed("Shirt");
    }

    private UUID variantNamed(String productName) {
        UUID variantId = UUID.randomUUID();
        commerce.put(
                variantId,
                new VariantLookupItem(
                        variantId,
                        UUID.randomUUID(),
                        productName,
                        "ACTIVE",
                        "ACTIVE",
                        "White",
                        "M",
                        100000L,
                        "/api/v1/variant-images/img/content",
                        true));
        return variantId;
    }

    private UUID seedOutfit(UUID ownerId, String name, OffsetDateTime updatedAt, UUID... variants) {
        UUID outfitId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO public.outfit(outfit_id, owner_account_id, name, creation_source, created_at, updated_at)
                VALUES (?, ?, ?, 'MANUAL', ?, ?)
                """,
                outfitId,
                ownerId,
                name,
                updatedAt,
                updatedAt);
        for (int position = 0; position < variants.length; position++) {
            jdbc.update(
                    """
                    INSERT INTO public.outfit_item(outfit_item_id, outfit_id, variant_id, display_order, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(),
                    outfitId,
                    variants[position],
                    position,
                    updatedAt);
        }
        return outfitId;
    }

    private UUID seedShare(
            UUID outfitId, String email, OffsetDateTime grantedAt, OffsetDateTime revokedAt) {
        UUID shareId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO public.outfit_share(outfit_share_id, outfit_id, recipient_email, granted_at, revoked_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                shareId,
                outfitId,
                email,
                grantedAt,
                revokedAt);
        return shareId;
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM public." + table, Integer.class);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, String body)
            throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }

    private void assertProblem(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
    }

    private String token(UUID subject, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .subject(subject.toString())
                        .claim("roles", List.of(role))
                        .issuedAt(now.minusSeconds(60))
                        .expiresAt(now.plusSeconds(300))
                        .build();
        JwsHeader header =
                JwsHeader.with(SignatureAlgorithm.RS256).keyId(SIGNING_KEY.getKeyID()).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(SIGNING_KEY)))
                .encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }

    private static RSAKey generateRsaKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JwtTestConfiguration {

        @Bean
        @Primary
        JwtDecoder shareTestJwtDecoder() throws Exception {
            return NimbusJwtDecoder.withPublicKey(SIGNING_KEY.toRSAPublicKey()).build();
        }
    }
}
