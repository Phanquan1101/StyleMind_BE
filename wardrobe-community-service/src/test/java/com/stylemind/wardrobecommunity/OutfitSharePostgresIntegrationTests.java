package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6")
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
        commerce.clear();
        accounts.clear();
        owner = account("Owner@Example.Test", "Linh Tran");
        viewer = account("viewer@example.test", "Minh Le");
        stranger = account("stranger@example.test", "Sam Pham");
        variant = variant();
        when(catalog.lookup(any())).thenAnswer(invocation -> {
            Collection<UUID> ids = invocation.getArgument(0);
            Map<UUID, VariantLookupItem> found = new LinkedHashMap<>();
            ids.forEach(id -> {
                if (commerce.containsKey(id)) {
                    found.put(id, commerce.get(id));
                }
            });
            return found;
        });
        when(identity.lookup(any())).thenAnswer(invocation -> {
            Collection<UUID> ids = invocation.getArgument(0);
            Map<UUID, AccountLookupItem> found = new LinkedHashMap<>();
            ids.forEach(id -> {
                if (accounts.containsKey(id)) {
                    found.put(id, accounts.get(id));
                }
            });
            return found;
        });
    }

    // ---------------------------------------------------------------- authentication and authorization

    @Test
    void sharingAndViewerRoutesNeedACustomerAndPublicRouteNeedsNothing() throws Exception {
        UUID outfitId = seedOutfit(owner, "Look", OffsetDateTime.now(), variant);
        String body = "{\"email\":\"viewer@example.test\"}";
        List<MockHttpServletRequestBuilder> routes = List.of(
                post(OUTFITS + "/" + outfitId + "/shares"), get(OUTFITS + "/" + outfitId + "/shares"),
                delete(OUTFITS + "/" + outfitId + "/shares/" + UUID.randomUUID()),
                put(OUTFITS + "/" + outfitId + "/public-link"), delete(OUTFITS + "/" + outfitId + "/public-link"),
                get(SHARED), get(SHARED + "/" + outfitId));
        for (MockHttpServletRequestBuilder route : routes) {
            send(route, null, body).andExpect(status().isUnauthorized());
        }
        String admin = token(UUID.randomUUID(), "ADMIN");
        for (MockHttpServletRequestBuilder route : routes) {
            send(route, admin, body).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        // The public route is open for GET only; any other method still needs a token.
        send(post(PUBLIC + "/anything"), null, "{}").andExpect(status().isUnauthorized());
        send(get(PUBLIC + "/anything"), null, null).andExpect(status().isNotFound());
        assertThat(count("outfit_share")).isZero();
    }

    // ---------------------------------------------------------------- sharing by email

    @Test
    void newShareReturns201NormalizesTheEmailAndSendsExactlyOneInvitation() throws Exception {
        UUID outfitId = seedOutfit(owner, "Weekend look", OffsetDateTime.now(), variant);

        JsonNode share = json(shareWith(owner, outfitId, "  Viewer@Example.TEST ").andExpect(status().isCreated()));

        assertThat(share.fieldNames()).toIterable().containsExactlyInAnyOrder("share_id", "email", "granted_at");
        assertThat(share.path("email").asText()).isEqualTo("viewer@example.test");
        assertThat(share.path("granted_at").asText()).isNotBlank();
        assertThat(count("outfit_share")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT recipient_email FROM public.outfit_share", String.class))
                .isEqualTo("viewer@example.test");
        verify(mail, times(1)).send("viewer@example.test", "Linh Tran", "Weekend look", outfitId);
    }

    @Test
    void sharingWithAnActiveRecipientReturns200WithTheSameShareAndNoSecondInvitation() throws Exception {
        UUID outfitId = seedOutfit(owner, "Look", OffsetDateTime.now(), variant);
        JsonNode first = json(shareWith(owner, outfitId, "viewer@example.test").andExpect(status().isCreated()));

        JsonNode again = json(shareWith(owner, outfitId, "VIEWER@example.test").andExpect(status().isOk()));

        assertThat(again.path("share_id").asText()).isEqualTo(first.path("share_id").asText());
        assertThat(again.path("granted_at").asText()).isEqualTo(first.path("granted_at").asText());
        assertThat(count("outfit_share")).isEqualTo(1);
        verify(mail, times(1)).send(any(), any(), any(), any());
    }

    @Test
    void sharingAgainAfterRevokeReturns201ReusesTheRowAndInvitesAgain() throws Exception {
        UUID outfitId = seedOutfit(owner, "Look", OffsetDateTime.now(), variant);
        JsonNode first = json(shareWith(owner, outfitId, "viewer@example.test").andExpect(status().isCreated()));
        UUID shareId = UUID.fromString(first.path("share_id").asText());
        send(delete(OUTFITS + "/" + outfitId + "/shares/" + shareId), token(owner, "CUSTOMER"), null)
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT revoked_at FROM public.outfit_share", OffsetDateTime.class)).isNotNull();

        JsonNode again = json(shareWith(owner, outfitId, "viewer@example.test").andExpect(status().isCreated()));

        assertThat(again.path("share_id").asText()).isEqualTo(shareId.toString());
        assertThat(count("outfit_share")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT revoked_at FROM public.outfit_share", OffsetDateTime.class)).isNull();
        verify(mail, times(2)).send("viewer@example.test", "Linh Tran", "Look", outfitId);
    }

    @Test
    void sharingWithTheOwnerIsRejected() throws Exception {
        UUID outfitId = seedOutfit(owner, "Look", OffsetDateTime.now(), variant);

        assertProblem(shareWith(owner, outfitId, "OWNER@example.test"), 400, "OUTFIT_SHARE_SELF");

        assertThat(count("outfit_share")).isZero();
        verify(mail, never()).send(any(), any(), any(), any());
    }

    @Test
    void twentyActiveSharesBlockTheNextOneButAnExistingRecipientStays200() throws Exception {
        UUID outfitId = seedOutfit(owner, "Look", OffsetDateTime.now(), variant);
        for (int i = 0; i < 20; i++) {
            seedShare(outfitId, "friend" + i + "@example.test", OffsetDateTime.now().minusDays(3), null);
        }

        assertProblem(shareWith(owner, outfitId, "one-more@example.test"), 409, "OUTFIT_SHARE_LIMIT_REACHED");
        shareWith(owner, outfitId, "friend3@example.test").andExpect(status().isOk());

        assertThat(count("outfit_share")).isEqualTo(20);
        // A revoked share frees a slot.
        jdbc.update("UPDATE public.outfit_share SET revoked_at = now() WHERE recipient_email = 'friend0@example.test'");
        shareWith(owner, outfitId, "one-more@example.test").andExpect(status().isCreated());
    }

    @Test
    void thirtyGrantsInTheLastDayAcrossOutfitsAreRateLimited() throws Exception {
        UUID target = seedOutfit(owner, "Target", OffsetDateTime.now(), variant);
        UUID other = seedOutfit(owner, "Other", OffsetDateTime.now(), variant);
        for (int i = 0; i < 30; i++) {
            // Revoked grants still count as grants.
            seedShare(other, "old" + i + "@example.test", OffsetDateTime.now().minusHours(2),
                    i % 2 == 0 ? OffsetDateTime.now().minusHours(1) : null);
        }

        assertProblem(shareWith(owner, target, "new@example.test"), 429, "SHARE_RATE_LIMITED");
        // An already active recipient is not a new grant.
        shareWith(owner, other, "old1@example.test").andExpect(status().isOk());

        jdbc.update("UPDATE public.outfit_share SET granted_at = now() - interval '25 hours'");
        shareWith(owner, target, "new@example.test").andExpect(status().isCreated());
    }

    @Test
    void invitationFailureDoesNotFailOrRollBackTheShare() throws Exception {
        UUID outfitId = seedOutfit(owner, "Look", OffsetDateTime.now(), variant);
        doThrow(new RuntimeException("notification down")).when(mail).send(any(), any(), any(), any());

        shareWith(owner, outfitId, "viewer@example.test").andExpect(status().isCreated());

        assertThat(count("outfit_share")).isEqualTo(1);
    }

    @Test
    void identityFailureIs503AndNothingIsWrittenOrSent() throws Exception {
        UUID outfitId = seedOutfit(owner, "Look", OffsetDateTime.now(), variant);
        seedShare(outfitId, "viewer@example.test", OffsetDateTime.now(), null);
        doThrow(new ApiOperationException(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_UNAVAILABLE",
                "Account directory is temporarily unavailable")).when(identity).lookup(any());

        assertProblem(shareWith(owner, outfitId, "new@example.test"), 503, "IDENTITY_UNAVAILABLE");
        assertProblem(send(get(SHARED), token(viewer, "CUSTOMER"), null), 503, "IDENTITY_UNAVAILABLE");
        assertProblem(send(get(SHARED + "/" + outfitId), token(viewer, "CUSTOMER"), null), 503, "IDENTITY_UNAVAILABLE");

        assertThat(count("outfit_share")).isEqualTo(1);
        verify(mail, never()).send(any(), any(), any(), any());
    }

    @Test
    void sharingValidatesTheBodyTheIdsAndOwnership() throws Exception {
        UUID outfitId = seedOutfit(owner, "Look", OffsetDateTime.now(), variant);
        UUID foreign = seedOutfit(stranger, "Not mine", OffsetDateTime.now(), variant);
        UUID foreignShare = seedShare(foreign, "someone@example.test", OffsetDateTime.now(), null);
        String ownerToken = token(owner, "CUSTOMER");
        String path = OUTFITS + "/" + outfitId + "/shares";

        for (String invalid : List.of("{}", "{\"email\":\"\"}", "{\"email\":\"   \"}", "{\"email\":\"nope\"}",
                "{\"email\":\"a@b\"}", "{\"email\":\"a@@example.test\"}", "{\"email\":\"a b@example.test\"}",
                "{\"email\":\"a@exa mple.test\"}", "{\"email\":null}", "{\"email\":5}",
                "{\"email\":\"" + "a".repeat(250) + "@example.test\"}",
                "{\"email\":\"" + "a".repeat(65) + "@example.test\"}",
                "{\"email\":\"viewer@example.test\",\"role\":\"ADMIN\"}", "{bad-json")) {
            assertProblem(send(post(path), ownerToken, invalid), 400, "INVALID_REQUEST");
        }
        assertThat(count("outfit_share")).isEqualTo(1);

        assertProblem(shareWith(owner, foreign, "viewer@example.test"), 404, "RESOURCE_NOT_FOUND");
        assertProblem(send(get(OUTFITS + "/" + foreign + "/shares"), ownerToken, null), 404, "RESOURCE_NOT_FOUND");
        assertProblem(send(delete(OUTFITS + "/" + foreign + "/shares/" + foreignShare), ownerToken, null), 404,
                "RESOURCE_NOT_FOUND");
        assertProblem(send(put(OUTFITS + "/" + foreign + "/public-link"), ownerToken, null), 404, "RESOURCE_NOT_FOUND");
        assertProblem(send(delete(OUTFITS + "/" + foreign + "/public-link"), ownerToken, null), 404,
                "RESOURCE_NOT_FOUND");
        // A share id that belongs to another outfit is 404 for the owner of this one.
        assertProblem(send(delete(path + "/" + foreignShare), ownerToken, null), 404, "RESOURCE_NOT_FOUND");
        assertProblem(send(get(OUTFITS + "/not-a-uuid/shares"), ownerToken, null), 400, "INVALID_REQUEST");
        assertProblem(send(delete(path + "/not-a-uuid"), ownerToken, null), 400, "INVALID_REQUEST");
        assertThat(jdbc.queryForObject("SELECT revoked_at FROM public.outfit_share WHERE outfit_share_id = ?",
                OffsetDateTime.class, foreignShare)).isNull();
    }

    @Test
    void listingSharesShowsOnlyActiveOnesAndRevokingTwiceIs404() throws Exception {
        UUID outfitId = seedOutfit(owner, "Look", OffsetDateTime.now(), variant);
        UUID active = seedShare(outfitId, "b@example.test", OffsetDateTime.now().minusHours(1), null);
        UUID newer = seedShare(outfitId, "c@example.test", OffsetDateTime.now(), null);
        UUID revoked = seedShare(outfitId, "a@example.test", OffsetDateTime.now().minusHours(2), OffsetDateTime.now());
        String ownerToken = token(owner, "CUSTOMER");

        JsonNode list = json(send(get(OUTFITS + "/" + outfitId + "/shares"), ownerToken, null)
                .andExpect(status().isOk()));
        assertThat(list.path("items")).hasSize(2);
        assertThat(list.path("items").get(0).path("share_id").asText()).isEqualTo(newer.toString());
        assertThat(list.path("items").get(1).path("share_id").asText()).isEqualTo(active.toString());
        assertThat(list.path("items").get(0).fieldNames()).toIterable()
                .containsExactlyInAnyOrder("share_id", "email", "granted_at");

        send(delete(OUTFITS + "/" + outfitId + "/shares/" + active), ownerToken, null).andExpect(status().isNoContent());
        assertProblem(send(delete(OUTFITS + "/" + outfitId + "/shares/" + active), ownerToken, null), 404,
                "RESOURCE_NOT_FOUND");
        assertProblem(send(delete(OUTFITS + "/" + outfitId + "/shares/" + revoked), ownerToken, null), 404,
                "RESOURCE_NOT_FOUND");
    }

    // ---------------------------------------------------------------- viewer

    @Test
    void viewerSeesASharedOutfitWithoutOwnerPrivateFields() throws Exception {
        UUID shirt = variantNamed("Linen Shirt");
        UUID outfitId = seedOutfit(owner, "Weekend look", OffsetDateTime.now(), shirt, variant);
        jdbc.update("UPDATE public.outfit SET public_token = 'secret-public-token-value' WHERE outfit_id = ?", outfitId);
        shareWith(owner, outfitId, "viewer@example.test").andExpect(status().isCreated());
        shareWith(owner, outfitId, "other-friend@example.test").andExpect(status().isCreated());
        String viewerToken = token(viewer, "CUSTOMER");

        JsonNode list = json(send(get(SHARED), viewerToken, null).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")));
        verify(catalog, never()).lookup(any());
        assertThat(list.path("total_elements").asLong()).isEqualTo(1);
        JsonNode summary = list.path("items").get(0);
        assertThat(summary.fieldNames()).toIterable().containsExactlyInAnyOrder("outfit_id", "name",
                "owner_display_name", "item_count", "granted_at");
        assertThat(summary.path("owner_display_name").asText()).isEqualTo("Linh Tran");
        assertThat(summary.path("item_count").asInt()).isEqualTo(2);

        ResultActions detail = send(get(SHARED + "/" + outfitId), viewerToken, null).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        String raw = detail.andReturn().getResponse().getContentAsString();
        JsonNode view = mapper.readTree(raw);
        assertThat(view.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("outfit_id", "name", "owner_display_name", "items");
        assertThat(view.path("owner_display_name").asText()).isEqualTo("Linh Tran");
        assertThat(view.path("items")).hasSize(2);
        assertThat(view.path("items").get(0).path("product_name").asText()).isEqualTo("Linen Shirt");
        assertThat(view.path("items").get(0).path("available").asBoolean()).isTrue();
        assertThat(raw).doesNotContain(owner.toString()).doesNotContain("owner@example.test")
                .doesNotContain("creation_source").doesNotContain("public_token").doesNotContain("secret-public-token-value")
                .doesNotContain("other-friend").doesNotContain("shares").doesNotContain("owner_account_id");
    }

    @Test
    void revokedOrForeignViewersAndTheOwnerGet404() throws Exception {
        UUID outfitId = seedOutfit(owner, "Look", OffsetDateTime.now(), variant);
        JsonNode share = json(shareWith(owner, outfitId, "viewer@example.test").andExpect(status().isCreated()));
        String viewerToken = token(viewer, "CUSTOMER");
        send(get(SHARED + "/" + outfitId), viewerToken, null).andExpect(status().isOk());

        assertProblem(send(get(SHARED + "/" + outfitId), token(stranger, "CUSTOMER"), null), 404, "RESOURCE_NOT_FOUND");
        assertProblem(send(get(SHARED + "/" + outfitId), token(owner, "CUSTOMER"), null), 404, "RESOURCE_NOT_FOUND");
        assertThat(json(send(get(SHARED), token(stranger, "CUSTOMER"), null)).path("items")).isEmpty();
        assertProblem(send(get(SHARED + "/" + UUID.randomUUID()), viewerToken, null), 404, "RESOURCE_NOT_FOUND");
        assertProblem(send(get(SHARED + "/not-a-uuid"), viewerToken, null), 400, "INVALID_REQUEST");

        send(delete(OUTFITS + "/" + outfitId + "/shares/" + share.path("share_id").asText()), token(owner, "CUSTOMER"),
                null).andExpect(status().isNoContent());
        assertProblem(send(get(SHARED + "/" + outfitId), viewerToken, null), 404, "RESOURCE_NOT_FOUND");
        assertThat(json(send(get(SHARED), viewerToken, null)).path("items")).isEmpty();
    }

    @Test
    void sharedListIsPagedByGrantNewestFirstAndRejectsBadPagination() throws Exception {
        UUID a = seedOutfit(owner, "A", OffsetDateTime.now(), variant);
        UUID b = seedOutfit(owner, "B", OffsetDateTime.now(), variant, variantNamed("Two"));
        UUID c = seedOutfit(owner, "C", OffsetDateTime.now(), variant);
        UUID revoked = seedOutfit(owner, "Revoked", OffsetDateTime.now(), variant);
        OffsetDateTime base = OffsetDateTime.now().minusDays(1);
        seedShare(a, "viewer@example.test", base, null);
        seedShare(b, "viewer@example.test", base.plusHours(1), null);
        seedShare(c, "viewer@example.test", base.plusHours(2), null);
        seedShare(revoked, "viewer@example.test", base.plusHours(3), OffsetDateTime.now());
        String viewerToken = token(viewer, "CUSTOMER");

        JsonNode first = json(send(get(SHARED + "?page=0&size=2"), viewerToken, null).andExpect(status().isOk()));
        assertThat(first.path("total_elements").asLong()).isEqualTo(3);
        assertThat(first.path("total_pages").asLong()).isEqualTo(2);
        assertThat(first.path("items").get(0).path("outfit_id").asText()).isEqualTo(c.toString());
        assertThat(first.path("items").get(1).path("outfit_id").asText()).isEqualTo(b.toString());
        assertThat(first.path("items").get(1).path("item_count").asInt()).isEqualTo(2);
        assertThat(json(send(get(SHARED + "?page=1&size=2"), viewerToken, null)).path("items").get(0)
                .path("outfit_id").asText()).isEqualTo(a.toString());
        for (String query : List.of("?page=-1", "?size=0", "?size=101", "?page=x", "?sort=name")) {
            assertProblem(send(get(SHARED + query), viewerToken, null), 400, "INVALID_REQUEST");
        }
    }

    @Test
    void commerceFailureIs503ForTheViewerDetailAndThePublicView() throws Exception {
        UUID outfitId = seedOutfit(owner, "Look", OffsetDateTime.now(), variant);
        seedShare(outfitId, "viewer@example.test", OffsetDateTime.now(), null);
        String publicToken = json(send(put(OUTFITS + "/" + outfitId + "/public-link"), token(owner, "CUSTOMER"), null)
                .andExpect(status().isOk())).path("public_token").asText();
        doThrow(new ApiOperationException(HttpStatus.SERVICE_UNAVAILABLE, "CATALOG_UNAVAILABLE",
                "Product catalog is temporarily unavailable")).when(catalog).lookup(any());

        assertProblem(send(get(SHARED + "/" + outfitId), token(viewer, "CUSTOMER"), null), 503, "CATALOG_UNAVAILABLE");
        assertProblem(send(get(PUBLIC + "/" + publicToken), null, null), 503, "CATALOG_UNAVAILABLE");
        // The shared list never needs Commerce.
        send(get(SHARED), token(viewer, "CUSTOMER"), null).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- public link

    @Test
    void publicLinkLifecycleIssuesNewTokensAndNeverReusesADisabledOne() throws Exception {
        UUID shirt = variantNamed("Linen Shirt");
        UUID outfitId = seedOutfit(owner, "Weekend look", OffsetDateTime.now(), shirt);
        String ownerToken = token(owner, "CUSTOMER");
        assertThat(json(send(get(OUTFITS + "/" + outfitId), ownerToken, null)).path("public_token").isNull()).isTrue();

        JsonNode enabled = json(send(put(OUTFITS + "/" + outfitId + "/public-link"), ownerToken, null)
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")));
        String first = enabled.path("public_token").asText();
        assertThat(first).matches("[A-Za-z0-9_-]{43}");
        assertThat(enabled.path("enabled_at").asText()).isNotBlank();
        assertThat(enabled.fieldNames()).toIterable().containsExactlyInAnyOrder("public_token", "enabled_at");
        assertThat(json(send(put(OUTFITS + "/" + outfitId + "/public-link"), ownerToken, null)).path("public_token")
                .asText()).isEqualTo(first);
        assertThat(json(send(get(OUTFITS + "/" + outfitId), ownerToken, null)).path("public_token").asText())
                .isEqualTo(first);

        ResultActions open = send(get(PUBLIC + "/" + first), null, null).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        JsonNode view = mapper.readTree(open.andReturn().getResponse().getContentAsString());
        assertThat(view.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("outfit_id", "name", "owner_display_name", "items");
        assertThat(view.path("owner_display_name").asText()).isEqualTo("Linh Tran");
        assertThat(view.path("items").get(0).path("product_name").asText()).isEqualTo("Linen Shirt");
        assertThat(open.andReturn().getResponse().getContentAsString()).doesNotContain(first)
                .doesNotContain(owner.toString()).doesNotContain("owner@example.test");

        send(delete(OUTFITS + "/" + outfitId + "/public-link"), ownerToken, null).andExpect(status().isNoContent());
        assertProblem(send(get(PUBLIC + "/" + first), null, null), 404, "RESOURCE_NOT_FOUND");
        assertThat(jdbc.queryForObject("SELECT public_token FROM public.outfit", String.class)).isNull();
        send(delete(OUTFITS + "/" + outfitId + "/public-link"), ownerToken, null).andExpect(status().isNoContent());

        String second = json(send(put(OUTFITS + "/" + outfitId + "/public-link"), ownerToken, null))
                .path("public_token").asText();
        assertThat(second).isNotEqualTo(first);
        assertProblem(send(get(PUBLIC + "/" + first), null, null), 404, "RESOURCE_NOT_FOUND");
        send(get(PUBLIC + "/" + second), null, null).andExpect(status().isOk());
        for (String bogus : List.of("short", "A".repeat(43), "A".repeat(200))) {
            assertProblem(send(get(PUBLIC + "/" + bogus), null, null), 404, "RESOURCE_NOT_FOUND");
        }
    }

    @Test
    void deletingTheOutfitRemovesItsSharesAndPublicLink() throws Exception {
        UUID outfitId = seedOutfit(owner, "Look", OffsetDateTime.now(), variant);
        shareWith(owner, outfitId, "viewer@example.test").andExpect(status().isCreated());
        String ownerToken = token(owner, "CUSTOMER");
        String publicToken = json(send(put(OUTFITS + "/" + outfitId + "/public-link"), ownerToken, null))
                .path("public_token").asText();
        assertThat(json(send(get(SHARED), token(viewer, "CUSTOMER"), null)).path("items")).hasSize(1);

        send(delete(OUTFITS + "/" + outfitId), ownerToken, null).andExpect(status().isNoContent());

        assertThat(json(send(get(SHARED), token(viewer, "CUSTOMER"), null)).path("items")).isEmpty();
        assertProblem(send(get(PUBLIC + "/" + publicToken), null, null), 404, "RESOURCE_NOT_FOUND");
        assertThat(count("outfit_share")).isZero();
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions shareWith(UUID ownerId, UUID outfitId, String email) throws Exception {
        return send(post(OUTFITS + "/" + outfitId + "/shares"), token(ownerId, "CUSTOMER"),
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
        commerce.put(variantId, new VariantLookupItem(variantId, UUID.randomUUID(), productName, "ACTIVE", "ACTIVE",
                "White", "M", 100000L, "/api/v1/variant-images/img/content", true));
        return variantId;
    }

    private UUID seedOutfit(UUID ownerId, String name, OffsetDateTime updatedAt, UUID... variants) {
        UUID outfitId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public.outfit(outfit_id, owner_account_id, name, creation_source, created_at, updated_at)
                VALUES (?, ?, ?, 'MANUAL', ?, ?)
                """, outfitId, ownerId, name, updatedAt, updatedAt);
        for (int position = 0; position < variants.length; position++) {
            jdbc.update("""
                    INSERT INTO public.outfit_item(outfit_item_id, outfit_id, variant_id, display_order, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), outfitId, variants[position], position, updatedAt);
        }
        return outfitId;
    }

    private UUID seedShare(UUID outfitId, String email, OffsetDateTime grantedAt, OffsetDateTime revokedAt) {
        UUID shareId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public.outfit_share(outfit_share_id, outfit_id, recipient_email, granted_at, revoked_at)
                VALUES (?, ?, ?, ?, ?)
                """, shareId, outfitId, email, grantedAt, revokedAt);
        return shareId;
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM public." + table, Integer.class);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
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
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject.toString())
                .claim("roles", List.of(role))
                .issuedAt(now.minusSeconds(60))
                .expiresAt(now.plusSeconds(300))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(SIGNING_KEY.getKeyID()).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(SIGNING_KEY)))
                .encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
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
