package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
@Import(OutfitPostgresIntegrationTests.JwtTestConfiguration.class)
class OutfitPostgresIntegrationTests {

    private static final String OUTFITS = "/api/v1/me/outfits";
    private static final RSAKey SIGNING_KEY = generateRsaKey();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6")
            .withDatabaseName("stylemind_wardrobe_community_outfit_test")
            .withUsername("wardrobe_community_outfit_test")
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

    /** What the mocked Commerce currently knows; variants absent from this map are "not in lookup". */
    private final Map<UUID, VariantLookupItem> commerce = new HashMap<>();
    private UUID owner;
    private UUID other;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM public.outfit_item");
        jdbc.update("DELETE FROM public.outfit");
        commerce.clear();
        owner = UUID.randomUUID();
        other = UUID.randomUUID();
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
    }

    // ---------------------------------------------------------------- authentication and authorization

    @Test
    void missingTokenIs401AndAdminIs403OnEveryRoute() throws Exception {
        UUID outfitId = seedOutfit(owner, "Seeded", OffsetDateTime.now(), variant("Shirt", true));
        UUID itemId = firstItem(outfitId);
        String admin = token(UUID.randomUUID(), "ADMIN");
        String itemBody = "{\"variant_id\":\"" + UUID.randomUUID() + "\"}";
        for (MockHttpServletRequestBuilder request : List.of(
                get(OUTFITS), get(OUTFITS + "/" + outfitId), delete(OUTFITS + "/" + outfitId),
                patch(OUTFITS + "/" + outfitId), post(OUTFITS), post(OUTFITS + "/" + outfitId + "/items"),
                patch(OUTFITS + "/" + outfitId + "/items/" + itemId),
                delete(OUTFITS + "/" + outfitId + "/items/" + itemId))) {
            send(request, null, itemBody).andExpect(status().isUnauthorized());
        }
        for (MockHttpServletRequestBuilder request : List.of(
                get(OUTFITS), get(OUTFITS + "/" + outfitId), delete(OUTFITS + "/" + outfitId),
                patch(OUTFITS + "/" + outfitId), post(OUTFITS), post(OUTFITS + "/" + outfitId + "/items"),
                patch(OUTFITS + "/" + outfitId + "/items/" + itemId),
                delete(OUTFITS + "/" + outfitId + "/items/" + itemId))) {
            send(request, admin, itemBody).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        assertThat(count("outfit")).isEqualTo(1);
    }

    // ---------------------------------------------------------------- create

    @Test
    void createValidOutfitReturns201WithDetailAndStoresOnlyReferences() throws Exception {
        UUID shirt = variant("Linen Shirt", true);
        UUID trousers = variant("Chinos", true);
        JsonNode created = json(send(post(OUTFITS), token(owner, "CUSTOMER"), createBody("  Weekend  ", "AI",
                shirt, trousers)).andExpect(status().isCreated()));

        assertThat(created.path("name").asText()).isEqualTo("Weekend");
        assertThat(created.path("creation_source").asText()).isEqualTo("AI");
        assertThat(created.path("created_at").asText()).isNotBlank();
        assertThat(created.path("items")).hasSize(2);
        assertThat(created.path("items").get(0).path("variant_id").asText()).isEqualTo(shirt.toString());
        assertThat(created.path("items").get(0).path("display_order").asInt()).isZero();
        assertThat(created.path("items").get(1).path("display_order").asInt()).isEqualTo(1);
        assertThat(created.path("items").get(0).path("product_name").asText()).isEqualTo("Linen Shirt");
        assertThat(created.path("items").get(0).path("available").asBoolean()).isTrue();
        assertThat(created.path("items").get(0).path("unit_price_vnd").asLong()).isEqualTo(100000L);
        UUID outfitId = UUID.fromString(created.path("outfit_id").asText());
        assertThat(jdbc.queryForObject("SELECT owner_account_id FROM public.outfit WHERE outfit_id = ?", UUID.class,
                outfitId)).isEqualTo(owner);
        assertThat(count("outfit_item")).isEqualTo(2);
        verify(catalog).lookup(List.of(shirt, trousers));
    }

    @Test
    void createRejectsMalformedRequestsWithoutCallingCommerceOrWriting() throws Exception {
        UUID v = variant("Shirt", true);
        String customer = token(owner, "CUSTOMER");
        List<String> invalid = new ArrayList<>(List.of(
                createBody("Name", "MANUAL"),
                createBody("Name", "MANUAL", elevenVariants()),
                createBody("Name", "MANUAL", v, v),
                createBody("   ", "MANUAL", v),
                createBody("", "MANUAL", v),
                createBody("x".repeat(101), "MANUAL", v),
                createBody("Name", "ROBOT", v),
                createBody("Name", "manual", v),
                "{\"name\":\"Name\",\"creation_source\":\"MANUAL\",\"items\":[{\"variant_id\":\"" + v
                        + "\"}],\"extra\":true}",
                "{\"name\":\"Name\",\"creation_source\":\"MANUAL\",\"items\":[{\"variant_id\":\"" + v
                        + "\",\"quantity\":2}]}",
                "{\"name\":\"Name\",\"creation_source\":\"MANUAL\",\"items\":[{\"variant_id\":\"not-a-uuid\"}]}",
                "{\"name\":\"Name\",\"creation_source\":\"MANUAL\",\"items\":[{\"variant_id\":null}]}",
                "{\"name\":\"Name\",\"creation_source\":\"MANUAL\",\"items\":\"nope\"}",
                "{\"name\":\"Name\\u0000\",\"creation_source\":\"MANUAL\",\"items\":[{\"variant_id\":\"" + v + "\"}]}",
                "{\"name\":\"Name\",\"creation_source\":\"MANUAL\"}",
                "{bad-json"));
        for (String body : invalid) {
            assertProblem(send(post(OUTFITS), customer, body), 400, "INVALID_REQUEST");
        }
        verify(catalog, never()).lookup(any());
        assertThat(count("outfit")).isZero();
        // The boundaries themselves are valid: 100-character name and exactly 10 items.
        send(post(OUTFITS), customer, createBody("x".repeat(100), "MANUAL", tenVariants()))
                .andExpect(status().isCreated());
    }

    @Test
    void createRequiresEveryVariantToBePurchasable() throws Exception {
        UUID active = variant("Shirt", true);
        UUID retired = variant("Retired", false);
        UUID unknown = UUID.randomUUID();
        String customer = token(owner, "CUSTOMER");
        assertProblem(send(post(OUTFITS), customer, createBody("A", "MANUAL", active, retired)), 409,
                "VARIANT_NOT_ACTIVE");
        assertProblem(send(post(OUTFITS), customer, createBody("A", "MANUAL", active, unknown)), 409,
                "VARIANT_NOT_ACTIVE");
        assertThat(count("outfit")).isZero();
        assertThat(count("outfit_item")).isZero();
    }

    @Test
    void ownerWith100OutfitsCannotCreateAnother() throws Exception {
        UUID v = variant("Shirt", true);
        for (int i = 0; i < 100; i++) {
            seedOutfit(owner, "Outfit " + i, OffsetDateTime.now(), v);
        }
        assertProblem(send(post(OUTFITS), token(owner, "CUSTOMER"), createBody("One more", "MANUAL", v)), 409,
                "OUTFIT_LIMIT_REACHED");
        verify(catalog, never()).lookup(any());
        // The limit is per owner.
        send(post(OUTFITS), token(other, "CUSTOMER"), createBody("Mine", "MANUAL", v)).andExpect(status().isCreated());
    }

    @Test
    void commerceFailureIs503CatalogUnavailable() throws Exception {
        UUID v = variant("Shirt", true);
        UUID outfitId = seedOutfit(owner, "Seeded", OffsetDateTime.now(), v);
        doThrow(new ApiOperationException(HttpStatus.SERVICE_UNAVAILABLE,
                "CATALOG_UNAVAILABLE", "Product catalog is temporarily unavailable")).when(catalog).lookup(any());
        String customer = token(owner, "CUSTOMER");
        assertProblem(send(post(OUTFITS), customer, createBody("A", "MANUAL", v)), 503, "CATALOG_UNAVAILABLE");
        assertProblem(send(get(OUTFITS + "/" + outfitId), customer, null), 503, "CATALOG_UNAVAILABLE");
        assertProblem(send(post(OUTFITS + "/" + outfitId + "/items"), customer,
                "{\"variant_id\":\"" + UUID.randomUUID() + "\"}"), 503, "CATALOG_UNAVAILABLE");
        assertThat(count("outfit")).isEqualTo(1);
        assertThat(count("outfit_item")).isEqualTo(1);
        // Listing never needs Commerce.
        send(get(OUTFITS), customer, null).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- list

    @Test
    void listIsPagedByUpdatedAtDescendingWithItemCountAndNeverCallsCommerce() throws Exception {
        UUID a = variant("A", true);
        UUID b = variant("B", true);
        OffsetDateTime base = OffsetDateTime.now().minusDays(1);
        UUID oldest = seedOutfit(owner, "Oldest", base, a);
        UUID middle = seedOutfit(owner, "Middle", base.plusHours(1), a, b);
        UUID newest = seedOutfit(owner, "Newest", base.plusHours(2), a);
        seedOutfit(other, "Someone else", base.plusHours(3), a);
        String customer = token(owner, "CUSTOMER");

        JsonNode first = json(send(get(OUTFITS + "?page=0&size=2"), customer, null).andExpect(status().isOk()));
        assertThat(first.path("page").asInt()).isZero();
        assertThat(first.path("size").asInt()).isEqualTo(2);
        assertThat(first.path("total_elements").asLong()).isEqualTo(3);
        assertThat(first.path("total_pages").asLong()).isEqualTo(2);
        assertThat(first.path("items")).hasSize(2);
        assertThat(first.path("items").get(0).path("outfit_id").asText()).isEqualTo(newest.toString());
        assertThat(first.path("items").get(1).path("outfit_id").asText()).isEqualTo(middle.toString());
        assertThat(first.path("items").get(1).path("item_count").asInt()).isEqualTo(2);
        assertThat(first.path("items").get(0).path("creation_source").asText()).isEqualTo("MANUAL");
        assertThat(first.path("items").get(0).has("items")).isFalse();

        JsonNode second = json(send(get(OUTFITS + "?page=1&size=2"), customer, null));
        assertThat(second.path("items")).hasSize(1);
        assertThat(second.path("items").get(0).path("outfit_id").asText()).isEqualTo(oldest.toString());
        JsonNode defaults = json(send(get(OUTFITS), customer, null));
        assertThat(defaults.path("size").asInt()).isEqualTo(20);
        assertThat(json(send(get(OUTFITS + "?page=9&size=2"), customer, null)).path("items")).isEmpty();
        verify(catalog, never()).lookup(any());
    }

    @Test
    void listRejectsInvalidPagination() throws Exception {
        String customer = token(owner, "CUSTOMER");
        for (String query : List.of("?page=-1", "?size=0", "?size=101", "?page=abc", "?size=1.5", "?sort=name",
                "?page=1&page=2", "?owner=" + other)) {
            assertProblem(send(get(OUTFITS + query), customer, null), 400, "INVALID_REQUEST");
        }
    }

    // ---------------------------------------------------------------- get

    @Test
    void getReturnsCurrentProductDataAndKeepsItemsCommerceNoLongerReturns() throws Exception {
        UUID live = variant("Linen Shirt", true);
        UUID retired = variant("Retired Jacket", false);
        UUID gone = UUID.randomUUID();
        UUID outfitId = seedOutfit(owner, "Mixed", OffsetDateTime.now(), live, retired, gone);

        JsonNode detail = json(send(get(OUTFITS + "/" + outfitId), token(owner, "CUSTOMER"), null)
                .andExpect(status().isOk()));

        JsonNode items = detail.path("items");
        assertThat(items).hasSize(3);
        assertThat(items.get(0).path("available").asBoolean()).isTrue();
        assertThat(items.get(0).path("image_url").asText()).isEqualTo("/api/v1/variant-images/img/content");
        assertThat(items.get(1).path("product_name").asText()).isEqualTo("Retired Jacket");
        assertThat(items.get(1).path("available").asBoolean()).isFalse();
        JsonNode missing = items.get(2);
        assertThat(missing.path("variant_id").asText()).isEqualTo(gone.toString());
        assertThat(missing.path("available").asBoolean()).isFalse();
        for (String field : List.of("product_id", "product_name", "color", "size", "unit_price_vnd", "image_url")) {
            assertThat(missing.path(field).isNull()).as(field).isTrue();
        }
        assertThat(count("outfit_item")).isEqualTo(3);
    }

    // ---------------------------------------------------------------- ownership

    @Test
    void anotherOwnersOutfitAndItemsAreNotFoundAndNeverChanged() throws Exception {
        UUID v = variant("Shirt", true);
        UUID spare = variant("Spare", true);
        UUID outfitId = seedOutfit(other, "Not mine", OffsetDateTime.now().minusHours(1), v);
        UUID itemId = firstItem(outfitId);
        UUID mineId = seedOutfit(owner, "Mine", OffsetDateTime.now(), spare);
        String customer = token(owner, "CUSTOMER");
        String body = "{\"variant_id\":\"" + spare + "\"}";
        OffsetDateTime before = updatedAt(outfitId);

        assertProblem(send(get(OUTFITS + "/" + outfitId), customer, null), 404, "RESOURCE_NOT_FOUND");
        assertProblem(send(patch(OUTFITS + "/" + outfitId), customer, "{\"name\":\"Hijack\"}"), 404,
                "RESOURCE_NOT_FOUND");
        assertProblem(send(delete(OUTFITS + "/" + outfitId), customer, null), 404, "RESOURCE_NOT_FOUND");
        assertProblem(send(post(OUTFITS + "/" + outfitId + "/items"), customer, body), 404, "RESOURCE_NOT_FOUND");
        assertProblem(send(patch(OUTFITS + "/" + outfitId + "/items/" + itemId), customer, body), 404,
                "RESOURCE_NOT_FOUND");
        assertProblem(send(delete(OUTFITS + "/" + outfitId + "/items/" + itemId), customer, null), 404,
                "RESOURCE_NOT_FOUND");
        // An item id that belongs to a different outfit is also 404, even for the same owner.
        assertProblem(send(patch(OUTFITS + "/" + mineId + "/items/" + itemId), customer, body), 404,
                "RESOURCE_NOT_FOUND");
        assertProblem(send(delete(OUTFITS + "/" + mineId + "/items/" + itemId), customer, null), 404,
                "RESOURCE_NOT_FOUND");
        assertProblem(send(get(OUTFITS + "/" + UUID.randomUUID()), customer, null), 404, "RESOURCE_NOT_FOUND");

        assertThat(jdbc.queryForObject("SELECT name FROM public.outfit WHERE outfit_id = ?", String.class, outfitId))
                .isEqualTo("Not mine");
        assertThat(updatedAt(outfitId)).isEqualTo(before);
        assertThat(count("outfit_item")).isEqualTo(2);
    }

    @Test
    void malformedIdsAreRejectedWith400() throws Exception {
        String customer = token(owner, "CUSTOMER");
        UUID id = UUID.randomUUID();
        assertProblem(send(get(OUTFITS + "/not-a-uuid"), customer, null), 400, "INVALID_REQUEST");
        assertProblem(send(delete(OUTFITS + "/not-a-uuid"), customer, null), 400, "INVALID_REQUEST");
        assertProblem(send(patch(OUTFITS + "/" + id + "/items/xyz"), customer,
                "{\"variant_id\":\"" + UUID.randomUUID() + "\"}"), 400, "INVALID_REQUEST");
        assertProblem(send(delete(OUTFITS + "/" + id + "/items/xyz"), customer, null), 400, "INVALID_REQUEST");
    }

    // ---------------------------------------------------------------- rename and delete

    @Test
    void renameChangesOnlyTheNameAndMovesTheOutfitToTheTop() throws Exception {
        UUID v = variant("Shirt", true);
        UUID first = seedOutfit(owner, "First", OffsetDateTime.now().minusHours(2), v);
        UUID second = seedOutfit(owner, "Second", OffsetDateTime.now().minusHours(1), v);
        String customer = token(owner, "CUSTOMER");

        JsonNode renamed = json(send(patch(OUTFITS + "/" + first), customer, "{\"name\":\"  Renamed  \"}")
                .andExpect(status().isOk()));
        assertThat(renamed.path("name").asText()).isEqualTo("Renamed");
        assertThat(renamed.path("item_count").asInt()).isEqualTo(1);
        assertThat(renamed.path("creation_source").asText()).isEqualTo("MANUAL");
        JsonNode list = json(send(get(OUTFITS), customer, null));
        assertThat(list.path("items").get(0).path("outfit_id").asText()).isEqualTo(first.toString());
        assertThat(list.path("items").get(1).path("outfit_id").asText()).isEqualTo(second.toString());

        for (String invalid : List.of("{\"name\":\"\"}", "{\"name\":\"   \"}", "{\"name\":\"" + "x".repeat(101) + "\"}",
                "{\"name\":\"ok\",\"creation_source\":\"AI\"}", "{\"creation_source\":\"AI\"}", "{}",
                "{\"name\":5}")) {
            assertProblem(send(patch(OUTFITS + "/" + first), customer, invalid), 400, "INVALID_REQUEST");
        }
        assertThat(jdbc.queryForObject("SELECT name FROM public.outfit WHERE outfit_id = ?", String.class, first))
                .isEqualTo("Renamed");
    }

    @Test
    void deleteRemovesTheOutfitAndItsItems() throws Exception {
        UUID v = variant("Shirt", true);
        UUID outfitId = seedOutfit(owner, "Doomed", OffsetDateTime.now(), v, variant("Hat", true));
        UUID kept = seedOutfit(owner, "Kept", OffsetDateTime.now(), v);
        String customer = token(owner, "CUSTOMER");

        send(delete(OUTFITS + "/" + outfitId), customer, null).andExpect(status().isNoContent());

        assertThat(count("outfit")).isEqualTo(1);
        assertThat(count("outfit_item")).isEqualTo(1);
        assertProblem(send(get(OUTFITS + "/" + outfitId), customer, null), 404, "RESOURCE_NOT_FOUND");
        send(get(OUTFITS + "/" + kept), customer, null).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- add item

    @Test
    void addItemAppendsAtMaxPlusOneAndTouchesUpdatedAt() throws Exception {
        UUID first = variant("Shirt", true);
        UUID second = variant("Chinos", true);
        UUID added = variant("Belt", true);
        OffsetDateTime old = OffsetDateTime.now().minusDays(1);
        UUID outfitId = seedOutfit(owner, "Look", old, first, second);
        jdbc.update("UPDATE public.outfit_item SET display_order = 5 WHERE variant_id = ? AND outfit_id = ?", second,
                outfitId);
        String customer = token(owner, "CUSTOMER");

        JsonNode detail = json(send(post(OUTFITS + "/" + outfitId + "/items"), customer,
                "{\"variant_id\":\"" + added + "\"}").andExpect(status().isCreated()));

        assertThat(detail.path("items")).hasSize(3);
        assertThat(detail.path("items").get(2).path("variant_id").asText()).isEqualTo(added.toString());
        assertThat(detail.path("items").get(2).path("display_order").asInt()).isEqualTo(6);
        assertThat(updatedAt(outfitId)).isAfter(old.plusHours(1));
    }

    @Test
    void addItemRejectsDuplicatesFullOutfitsAndInactiveVariants() throws Exception {
        List<UUID> ten = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ten.add(variant("Piece " + i, true));
        }
        UUID full = seedOutfit(owner, "Full", OffsetDateTime.now(), ten.toArray(UUID[]::new));
        UUID partial = seedOutfit(owner, "Partial", OffsetDateTime.now(), ten.get(0));
        UUID retired = variant("Retired", false);
        UUID extra = variant("Extra", true);
        String customer = token(owner, "CUSTOMER");

        assertProblem(send(post(OUTFITS + "/" + full + "/items"), customer, "{\"variant_id\":\"" + extra + "\"}"),
                409, "OUTFIT_ITEM_LIMIT_REACHED");
        assertProblem(send(post(OUTFITS + "/" + partial + "/items"), customer,
                "{\"variant_id\":\"" + ten.get(0) + "\"}"), 409, "OUTFIT_ITEM_DUPLICATE");
        assertProblem(send(post(OUTFITS + "/" + partial + "/items"), customer, "{\"variant_id\":\"" + retired + "\"}"),
                409, "VARIANT_NOT_ACTIVE");
        assertProblem(send(post(OUTFITS + "/" + partial + "/items"), customer,
                "{\"variant_id\":\"" + UUID.randomUUID() + "\"}"), 409, "VARIANT_NOT_ACTIVE");
        for (String invalid : List.of("{}", "{\"variant_id\":\"nope\"}", "{\"variant_id\":null}",
                "{\"variant_id\":\"" + extra + "\",\"x\":1}")) {
            assertProblem(send(post(OUTFITS + "/" + partial + "/items"), customer, invalid), 400, "INVALID_REQUEST");
        }
        assertThat(itemCount(full)).isEqualTo(10);
        assertThat(itemCount(partial)).isEqualTo(1);
    }

    @Test
    void concurrentAddsNeverExceedTenItems() throws Exception {
        List<UUID> nine = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            nine.add(variant("Piece " + i, true));
        }
        UUID outfitId = seedOutfit(owner, "Nearly full", OffsetDateTime.now(), nine.toArray(UUID[]::new));
        String customer = token(owner, "CUSTOMER");
        List<UUID> contenders = List.of(variant("Late A", true), variant("Late B", true), variant("Late C", true));

        ExecutorService pool = Executors.newFixedThreadPool(contenders.size());
        try {
            List<Future<MockHttpResult>> futures = new ArrayList<>();
            for (UUID variantId : contenders) {
                Callable<MockHttpResult> add = () -> {
                    var response = send(post(OUTFITS + "/" + outfitId + "/items"), customer,
                            "{\"variant_id\":\"" + variantId + "\"}").andReturn().getResponse();
                    return new MockHttpResult(response.getStatus(), response.getContentAsString());
                };
                futures.add(pool.submit(add));
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<MockHttpResult> future : futures) {
                MockHttpResult result = future.get(30, TimeUnit.SECONDS);
                statuses.add(result.status());
                if (result.status() == 409) {
                    assertThat(mapper.readTree(result.body()).path("code").asText())
                            .isEqualTo("OUTFIT_ITEM_LIMIT_REACHED");
                }
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 409, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(itemCount(outfitId)).isEqualTo(10);
    }

    // ---------------------------------------------------------------- change item variant

    @Test
    void changeItemSwitchesToAnotherVariantOfTheSameProduct() throws Exception {
        UUID product = UUID.randomUUID();
        UUID white = variantOfProduct(product, "White", true);
        UUID black = variantOfProduct(product, "Black", true);
        UUID other1 = variant("Trousers", true);
        OffsetDateTime old = OffsetDateTime.now().minusDays(1);
        UUID outfitId = seedOutfit(owner, "Look", old, white, other1);
        UUID itemId = itemOf(outfitId, white);

        JsonNode detail = json(send(patch(OUTFITS + "/" + outfitId + "/items/" + itemId), token(owner, "CUSTOMER"),
                "{\"variant_id\":\"" + black + "\"}").andExpect(status().isOk()));

        assertThat(detail.path("items").get(0).path("outfit_item_id").asText()).isEqualTo(itemId.toString());
        assertThat(detail.path("items").get(0).path("variant_id").asText()).isEqualTo(black.toString());
        assertThat(detail.path("items").get(0).path("color").asText()).isEqualTo("Black");
        assertThat(detail.path("items").get(0).path("display_order").asInt()).isZero();
        assertThat(detail.path("items")).hasSize(2);
        assertThat(updatedAt(outfitId)).isAfter(old.plusHours(1));
    }

    @Test
    void changeItemRulesAreEnforcedInTheDocumentedOrder() throws Exception {
        UUID product = UUID.randomUUID();
        UUID white = variantOfProduct(product, "White", true);
        UUID black = variantOfProduct(product, "Black", true);
        UUID retiredSibling = variantOfProduct(product, "Retired", false);
        UUID otherProduct = variant("Different", true);
        UUID outfitId = seedOutfit(owner, "Look", OffsetDateTime.now().minusHours(1), white, black);
        UUID whiteItem = itemOf(outfitId, white);
        String customer = token(owner, "CUSTOMER");
        String path = OUTFITS + "/" + outfitId + "/items/" + whiteItem;
        OffsetDateTime before = updatedAt(outfitId);

        assertProblem(send(patch(path), customer, "{\"variant_id\":\"" + otherProduct + "\"}"), 400, "INVALID_REQUEST");
        assertProblem(send(patch(path), customer, "{\"variant_id\":\"" + retiredSibling + "\"}"), 409,
                "VARIANT_NOT_ACTIVE");
        assertProblem(send(patch(path), customer, "{\"variant_id\":\"" + UUID.randomUUID() + "\"}"), 409,
                "VARIANT_NOT_ACTIVE");
        assertProblem(send(patch(path), customer, "{\"variant_id\":\"" + black + "\"}"), 409, "OUTFIT_ITEM_DUPLICATE");
        for (String invalid : List.of("{}", "{\"variant_id\":\"nope\"}", "{\"variant_id\":\"" + black + "\",\"x\":1}")) {
            assertProblem(send(patch(path), customer, invalid), 400, "INVALID_REQUEST");
        }
        assertThat(updatedAt(outfitId)).isEqualTo(before);

        // Re-selecting the current variant is a successful no-op.
        JsonNode same = json(send(patch(path), customer, "{\"variant_id\":\"" + white + "\"}")
                .andExpect(status().isOk()));
        assertThat(same.path("items").get(0).path("variant_id").asText()).isEqualTo(white.toString());
        assertThat(updatedAt(outfitId)).isEqualTo(before);
    }

    @Test
    void changeItemNeedsTheCurrentVariantInCommerceButNotNecessarilyPurchasable() throws Exception {
        UUID product = UUID.randomUUID();
        UUID retiredCurrent = variantOfProduct(product, "Old", false);
        UUID replacement = variantOfProduct(product, "New", true);
        UUID vanished = UUID.randomUUID();
        UUID outfitId = seedOutfit(owner, "Look", OffsetDateTime.now(), retiredCurrent, vanished);
        String customer = token(owner, "CUSTOMER");

        // Still known to Commerce but no longer on sale: switching to a valid sibling is allowed.
        send(patch(OUTFITS + "/" + outfitId + "/items/" + itemOf(outfitId, retiredCurrent)), customer,
                "{\"variant_id\":\"" + replacement + "\"}").andExpect(status().isOk());
        // Unknown to Commerce: the item must be removed and added again.
        assertProblem(send(patch(OUTFITS + "/" + outfitId + "/items/" + itemOf(outfitId, vanished)), customer,
                "{\"variant_id\":\"" + variantOfProduct(product, "Other", true) + "\"}"), 409,
                "OUTFIT_ITEM_UNAVAILABLE");
    }

    // ---------------------------------------------------------------- remove item

    @Test
    void removeItemDeletesItButNeverTheLastOne() throws Exception {
        UUID a = variant("A", true);
        UUID b = variant("B", true);
        OffsetDateTime old = OffsetDateTime.now().minusDays(1);
        UUID outfitId = seedOutfit(owner, "Look", old, a, b);
        String customer = token(owner, "CUSTOMER");

        send(delete(OUTFITS + "/" + outfitId + "/items/" + itemOf(outfitId, a)), customer, null)
                .andExpect(status().isNoContent());
        assertThat(itemCount(outfitId)).isEqualTo(1);
        assertThat(updatedAt(outfitId)).isAfter(old.plusHours(1));

        OffsetDateTime afterFirst = updatedAt(outfitId);
        assertProblem(send(delete(OUTFITS + "/" + outfitId + "/items/" + itemOf(outfitId, b)), customer, null), 409,
                "OUTFIT_LAST_ITEM");
        assertThat(itemCount(outfitId)).isEqualTo(1);
        assertThat(updatedAt(outfitId)).isEqualTo(afterFirst);
    }

    // ---------------------------------------------------------------- helpers

    private record MockHttpResult(int status, String body) {
    }

    private UUID variant(String productName, boolean purchasable) {
        return variantOfProduct(UUID.randomUUID(), productName, "White", purchasable);
    }

    /** Variants that share a product id are colors of one product. */
    private UUID variantOfProduct(UUID productId, String color, boolean purchasable) {
        return variantOfProduct(productId, "Shared Product", color, purchasable);
    }

    private UUID variantOfProduct(UUID productId, String productName, String color, boolean purchasable) {
        UUID variantId = UUID.randomUUID();
        commerce.put(variantId, new VariantLookupItem(variantId, productId, productName,
                purchasable ? "ACTIVE" : "INACTIVE", purchasable ? "ACTIVE" : "INACTIVE", color, "M", 100000L,
                "/api/v1/variant-images/img/content", purchasable));
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

    private UUID firstItem(UUID outfitId) {
        return jdbc.queryForObject("SELECT outfit_item_id FROM public.outfit_item WHERE outfit_id = ? "
                + "ORDER BY display_order LIMIT 1", UUID.class, outfitId);
    }

    private UUID itemOf(UUID outfitId, UUID variantId) {
        return jdbc.queryForObject("SELECT outfit_item_id FROM public.outfit_item WHERE outfit_id = ? "
                + "AND variant_id = ?", UUID.class, outfitId, variantId);
    }

    private OffsetDateTime updatedAt(UUID outfitId) {
        return jdbc.queryForObject("SELECT updated_at FROM public.outfit WHERE outfit_id = ?", OffsetDateTime.class,
                outfitId);
    }

    private int itemCount(UUID outfitId) {
        return jdbc.queryForObject("SELECT count(*) FROM public.outfit_item WHERE outfit_id = ?", Integer.class,
                outfitId);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM public." + table, Integer.class);
    }

    private UUID[] elevenVariants() {
        return variants(11);
    }

    private UUID[] tenVariants() {
        return variants(10);
    }

    private UUID[] variants(int amount) {
        UUID[] ids = new UUID[amount];
        for (int i = 0; i < amount; i++) {
            ids[i] = variant("Piece " + i, true);
        }
        return ids;
    }

    private String createBody(String name, String source, UUID... variantIds) throws Exception {
        List<Map<String, String>> items = new ArrayList<>();
        for (UUID id : variantIds) {
            items.add(Map.of("variant_id", id.toString()));
        }
        return mapper.writeValueAsString(Map.of("name", name, "creation_source", source, "items", items));
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
        JwtDecoder outfitTestJwtDecoder() throws Exception {
            return NimbusJwtDecoder.withPublicKey(SIGNING_KEY.toRSAPublicKey()).build();
        }
    }
}
