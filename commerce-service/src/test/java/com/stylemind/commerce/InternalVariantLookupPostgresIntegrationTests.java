package com.stylemind.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "management.health.db.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class InternalVariantLookupPostgresIntegrationTests {

    private static final String PATH = "/internal/v1/variants/batch-lookup";
    private static final String TOKEN_HEADER = "X-StyleMind-Service-Token";
    private static final String SERVICE_TOKEN = "test-only-internal-service-token-0123456789";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6")
            .withDatabaseName("stylemind_commerce_internal_test")
            .withUsername("commerce_internal_test")
            .withPassword("test-only-not-a-secret");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("stylemind.security.service-token", () -> SERVICE_TOKEN);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    private UUID activeVariantId;
    private UUID inactiveProductVariantId;
    private UUID inactiveVariantId;
    private UUID inactiveBrandVariantId;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM public.variant_image");
        jdbc.update("DELETE FROM public.product_variant");
        jdbc.update("DELETE FROM public.product");
        jdbc.update("DELETE FROM public.category");
        jdbc.update("DELETE FROM public.brand");
        UUID categoryId = UUID.randomUUID();
        UUID brandId = UUID.randomUUID();
        UUID inactiveBrandId = UUID.randomUUID();
        UUID activeProductId = UUID.randomUUID();
        UUID inactiveProductId = UUID.randomUUID();
        UUID inactiveBrandProductId = UUID.randomUUID();
        activeVariantId = UUID.randomUUID();
        inactiveVariantId = UUID.randomUUID();
        inactiveProductVariantId = UUID.randomUUID();
        inactiveBrandVariantId = UUID.randomUUID();
        jdbc.update("INSERT INTO public.category(category_id, name, slug, is_active) VALUES (?, 'Tops', 'tops', true)",
                categoryId);
        jdbc.update("INSERT INTO public.brand(brand_id, name, slug, is_active) VALUES (?, 'StyleMind', 'stylemind', true)",
                brandId);
        jdbc.update("INSERT INTO public.brand(brand_id, name, slug, is_active) VALUES (?, 'Old', 'old', false)",
                inactiveBrandId);
        insertProduct(activeProductId, categoryId, brandId, "Linen Shirt", "ACTIVE");
        insertProduct(inactiveProductId, categoryId, brandId, "Retired Shirt", "INACTIVE");
        insertProduct(inactiveBrandProductId, categoryId, inactiveBrandId, "Old Brand Shirt", "ACTIVE");
        insertVariant(activeVariantId, activeProductId, "SKU-ACTIVE", "ACTIVE");
        insertVariant(inactiveVariantId, activeProductId, "SKU-INACTIVE", "INACTIVE");
        insertVariant(inactiveProductVariantId, inactiveProductId, "SKU-RETIRED", "ACTIVE");
        insertVariant(inactiveBrandVariantId, inactiveBrandProductId, "SKU-OLDBRAND", "ACTIVE");
        jdbc.update("""
                INSERT INTO public.variant_image(image_id, variant_id, image_url, display_order, is_primary)
                VALUES (?, ?, '/api/v1/variant-images/second/content', 1, false),
                       (?, ?, '/api/v1/variant-images/primary/content', 0, true)
                """, UUID.randomUUID(), activeVariantId, UUID.randomUUID(), activeVariantId);
    }

    @Test
    void missingOrWrongTokenReturns401() throws Exception {
        String body = body(activeVariantId);
        perform(post(PATH), body).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVICE_UNAUTHORIZED"));
        perform(post(PATH).header(TOKEN_HEADER, "wrong-service-token-value-that-is-invalid-xx"), body)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVICE_UNAUTHORIZED"));
    }

    @Test
    void returnsFullVariantDetailsAndPrimaryImageForActiveVariant() throws Exception {
        JsonNode item = items(body(activeVariantId)).get(0);

        assertThat(item.path("variant_id").asText()).isEqualTo(activeVariantId.toString());
        assertThat(item.path("product_name").asText()).isEqualTo("Linen Shirt");
        assertThat(item.path("product_status").asText()).isEqualTo("ACTIVE");
        assertThat(item.path("variant_status").asText()).isEqualTo("ACTIVE");
        assertThat(item.path("color").asText()).isEqualTo("White");
        assertThat(item.path("size").asText()).isEqualTo("M");
        assertThat(item.path("unit_price_vnd").asLong()).isEqualTo(100000L);
        assertThat(item.path("primary_image_url").asText()).isEqualTo("/api/v1/variant-images/primary/content");
        assertThat(item.path("purchasable").asBoolean()).isTrue();
        assertThat(item.path("product_id").asText()).isNotBlank();
    }

    @Test
    void inactiveVariantProductOrBrandAreReturnedButNotPurchasable() throws Exception {
        JsonNode items = items(body(inactiveVariantId, inactiveProductVariantId, inactiveBrandVariantId));

        assertThat(items).hasSize(3);
        items.forEach(item -> assertThat(item.path("purchasable").asBoolean()).isFalse());
        JsonNode retired = find(items, inactiveProductVariantId);
        assertThat(retired.path("product_status").asText()).isEqualTo("INACTIVE");
        assertThat(retired.path("variant_status").asText()).isEqualTo("ACTIVE");
        assertThat(find(items, inactiveVariantId).path("variant_status").asText()).isEqualTo("INACTIVE");
        assertThat(find(items, inactiveVariantId).path("primary_image_url").isNull()).isTrue();
    }

    @Test
    void unknownIdsAreSkippedAndDuplicatesCollapsed() throws Exception {
        JsonNode items = items(body(UUID.randomUUID(), activeVariantId, activeVariantId));

        assertThat(items).hasSize(1);
        assertThat(items.get(0).path("variant_id").asText()).isEqualTo(activeVariantId.toString());
        assertThat(items(body(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void invalidBodiesReturn400() throws Exception {
        String over = mapper.writeValueAsString(java.util.Map.of("variant_ids",
                IntStream.range(0, 101).mapToObj(i -> UUID.randomUUID().toString()).toList()));
        for (String invalid : List.of(over, "{\"variant_ids\":[]}", "{\"variant_ids\":[\"not-a-uuid\"]}",
                "{\"variant_ids\":[null]}", "{}", "{bad-json")) {
            internal(invalid).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        String hundred = mapper.writeValueAsString(java.util.Map.of("variant_ids",
                IntStream.range(0, 100).mapToObj(i -> UUID.randomUUID().toString()).toList()));
        internal(hundred).andExpect(status().isOk());
    }

    private JsonNode items(String body) throws Exception {
        return mapper.readTree(internal(body).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsByteArray()).path("items");
    }

    private ResultActions internal(String body) throws Exception {
        return perform(post(PATH).header(TOKEN_HEADER, SERVICE_TOKEN), body);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String body(UUID... ids) throws Exception {
        return mapper.writeValueAsString(java.util.Map.of("variant_ids",
                java.util.Arrays.stream(ids).map(UUID::toString).toList()));
    }

    private JsonNode find(JsonNode items, UUID variantId) {
        for (JsonNode item : items) {
            if (variantId.toString().equals(item.path("variant_id").asText())) {
                return item;
            }
        }
        throw new AssertionError("Variant not in response: " + variantId);
    }

    private void insertProduct(UUID id, UUID categoryId, UUID brandId, String name, String status) {
        jdbc.update("""
                INSERT INTO public.product(product_id, category_id, brand_id, name, product_status)
                VALUES (?, ?, ?, ?, ?)
                """, id, categoryId, brandId, name, status);
    }

    private void insertVariant(UUID id, UUID productId, String sku, String status) {
        jdbc.update("""
                INSERT INTO public.product_variant(variant_id, product_id, sku, color, size, unit_price_vnd, variant_status)
                VALUES (?, ?, ?, 'White', 'M', 100000, ?)
                """, id, productId, sku, status);
    }
}
