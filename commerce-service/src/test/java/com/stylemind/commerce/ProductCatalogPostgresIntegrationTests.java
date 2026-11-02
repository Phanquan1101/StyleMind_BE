package com.stylemind.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "management.health.db.enabled=false",
        "stylemind.commerce.image-storage.local-directory=target/test-variant-images"
})
@AutoConfigureMockMvc
@Testcontainers
@Import(ProductCatalogPostgresIntegrationTests.JwtTestConfiguration.class)
class ProductCatalogPostgresIntegrationTests {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6")
            .withDatabaseName("stylemind_commerce_product_test")
            .withUsername("commerce_product_test")
            .withPassword("test-only-not-a-secret");
    private static final RSAKey SIGNING_KEY = generateRsaKey();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES_CONTAINER = POSTGRES;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    private UUID categoryId;
    private UUID brandId;

    @BeforeEach
    void clearDisposableDatabase() {
        jdbc.update("DELETE FROM public.variant_image");
        jdbc.update("DELETE FROM public.product_variant");
        jdbc.update("DELETE FROM public.product");
        jdbc.update("DELETE FROM public.category");
        jdbc.update("DELETE FROM public.brand");
        categoryId = UUID.randomUUID();
        brandId = UUID.randomUUID();
        jdbc.update("INSERT INTO public.category(category_id, name, slug, is_active) VALUES (?, ?, ?, true)",
                categoryId, "Tops", "tops");
        jdbc.update("INSERT INTO public.brand(brand_id, name, slug, is_active) VALUES (?, ?, ?, true)",
                brandId, "StyleMind", "stylemind");
    }

    @Test
    void adminProductAndVariantCrudValidateReferencesStatusPriceSkuAndAccess() throws Exception {
        assertProblem(postJson("/api/v1/admin/products", customerToken(), productBody("ACTIVE")), 403, "ACCESS_DENIED");
        JsonNode created = json(postJson("/api/v1/admin/products", adminToken(), productBody("ACTIVE")));
        UUID productId = UUID.fromString(created.path("product_id").asText());
        assertThat(created.path("category_id").asText()).isEqualTo(categoryId.toString());

        mockMvc.perform(put("/api/v1/admin/products/{id}", productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody("INACTIVE").replace("Linen Shirt", "Linen Overshirt")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product_status").value("INACTIVE"));
        mockMvc.perform(put("/api/v1/admin/products/{id}", productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody("ACTIVE").replace("Linen Shirt", "Linen Overshirt")))
                .andExpect(status().isOk());

        JsonNode variant = json(postJson("/api/v1/admin/products/" + productId + "/variants", adminToken(),
                variantBody("SKU-001", 199000, "ACTIVE")));
        UUID variantId = UUID.fromString(variant.path("variant_id").asText());
        mockMvc.perform(put("/api/v1/admin/variants/{id}", variantId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(variantBody("SKU-001-EDIT", 209000, "ACTIVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unit_price_vnd").value(209000));

        assertProblem(postJson("/api/v1/admin/products/" + productId + "/variants", adminToken(),
                variantBody("SKU-001-EDIT", 219000, "ACTIVE")), 409, "DUPLICATE_SKU");
        assertProblem(postJson("/api/v1/admin/products/" + productId + "/variants", adminToken(),
                variantBody("SKU-BAD", -1, "ACTIVE")), 400, "INVALID_REQUEST");
        assertProblem(postJson("/api/v1/admin/products/" + productId + "/variants", adminToken(),
                variantBody("SKU-002", 1, "ARCHIVED")), 400, "INVALID_REQUEST");

        mockMvc.perform(delete("/api/v1/admin/variants/{id}", variantId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/admin/products/{id}", productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isNoContent());
    }

    @Test
    void publicProductViewsFilterPaginateAndExcludeInactiveProductsAndVariants() throws Exception {
        UUID active = product("White Linen Shirt", "ACTIVE", "WOMEN");
        UUID inactive = product("Hidden Linen Shirt", "INACTIVE", "WOMEN");
        UUID variantActive = variant(active, "SKU-ACTIVE", 150000, "ACTIVE");
        UUID variantInactive = variant(active, "SKU-HIDDEN", 99000, "INACTIVE");
        variant(inactive, "SKU-INACTIVE-PRODUCT", 120000, "ACTIVE");

        mockMvc.perform(get("/api/v1/products")
                        .queryParam("keyword", "linen")
                        .queryParam("category_id", categoryId.toString())
                        .queryParam("min_price_vnd", "100000")
                        .queryParam("max_price_vnd", "200000")
                        .queryParam("demographic_segment", "WOMEN")
                        .queryParam("page", "0")
                        .queryParam("size", "10")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(1))
                .andExpect(jsonPath("$.items[0].product_id").value(active.toString()))
                .andExpect(jsonPath("$.items[0].variants.length()").value(1))
                .andExpect(jsonPath("$.items[0].variants[0].variant_id").value(variantActive.toString()));

        mockMvc.perform(get("/api/v1/products")
                        .queryParam("keyword", "missing")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(get("/api/v1/products/{id}", inactive)
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken())))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/variants/batch-lookup")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variant_ids\":[\"" + variantActive + "\",\"" + variantInactive + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].unit_price_vnd").value(150000));
    }

    @Test
    void variantImagesUploadReorderDeleteAndRejectInvalidFiles() throws Exception {
        UUID productId = product("Image Product", "ACTIVE", null);
        UUID variantId = variant(productId, "SKU-IMG", 100000, "ACTIVE");
        MockMultipartFile text = new MockMultipartFile("file", "bad.txt", "text/plain", "not-image".getBytes());
        mockMvc.perform(multipart("/api/v1/admin/variants/{id}/images", variantId)
                        .file(text)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        JsonNode first = uploadImage(variantId, "first.png", new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        JsonNode second = uploadImage(variantId, "second.png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 2});
        UUID firstId = UUID.fromString(first.path("image_id").asText());
        UUID secondId = UUID.fromString(second.path("image_id").asText());
        assertThat(first.path("is_primary").asBoolean()).isTrue();
        assertThat(second.path("is_primary").asBoolean()).isFalse();

        mockMvc.perform(put("/api/v1/admin/variants/{id}/images/reorder", variantId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[
                                  {"image_id":"%s","display_order":1},
                                  {"image_id":"%s","display_order":0}
                                ],"primary_image_id":"%s"}
                                """.formatted(firstId, secondId, secondId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].image_id").value(secondId.toString()))
                .andExpect(jsonPath("$[0].is_primary").value(true));
        mockMvc.perform(get("/api/v1/variant-images/{id}/content", secondId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken())))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/admin/variant-images/{id}", firstId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isNoContent());
    }

    private JsonNode uploadImage(UUID variantId, String name, byte[] bytes) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", name, "image/png", bytes);
        MvcResult result = mockMvc.perform(multipart("/api/v1/admin/variants/{id}/images", variantId)
                        .file(file)
                        .param("alt_text", "Image")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result);
    }

    private UUID product(String name, String status, String demographic) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public.product (
                    product_id, category_id, brand_id, name, demographic_segment, product_status
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, id, categoryId, brandId, name, demographic, status);
        return id;
    }

    private UUID variant(UUID productId, String sku, long price, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public.product_variant (
                    variant_id, product_id, sku, unit_price_vnd, variant_status
                ) VALUES (?, ?, ?, ?, ?)
                """, id, productId, sku, price, status);
        return id;
    }

    private String productBody(String status) {
        return """
                {
                  "category_id": "%s",
                  "brand_id": "%s",
                  "name": "Linen Shirt",
                  "description": "Lightweight linen",
                  "demographic_segment": "WOMEN",
                  "product_status": "%s"
                }
                """.formatted(categoryId, brandId, status);
    }

    private String variantBody(String sku, long price, String status) {
        return """
                {
                  "sku": "%s",
                  "size": "M",
                  "color": "White",
                  "material": "Linen",
                  "unit_price_vnd": %d,
                  "variant_status": "%s"
                }
                """.formatted(sku, price, status);
    }

    private MvcResult postJson(String path, String token, String body) throws Exception {
        return mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private void assertProblem(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(json(result).path("code").asText()).isEqualTo(code);
    }

    private String adminToken() {
        return token("ADMIN");
    }

    private String customerToken() {
        return token("CUSTOMER");
    }

    private String token(String role) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now().minusSeconds(1))
                .expiresAt(Instant.now().plusSeconds(600))
                .claim("roles", List.of(role))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(SIGNING_KEY.getKeyID()).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(SIGNING_KEY)))
                .encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private String bearer(String token) {
        return "Bearer " + token;
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
        JwtDecoder testJwtDecoder() throws Exception {
            return NimbusJwtDecoder.withPublicKey(SIGNING_KEY.toRSAPublicKey()).build();
        }
    }
}
