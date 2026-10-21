package com.stylemind.commerce;

import static org.assertj.core.api.Assertions.assertThat;
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

@SpringBootTest(properties = "management.health.db.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
@Import(CatalogReferencePostgresIntegrationTests.JwtTestConfiguration.class)
class CatalogReferencePostgresIntegrationTests {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6")
            .withDatabaseName("stylemind_commerce_catalog_test")
            .withUsername("commerce_catalog_test")
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
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void clearDisposableDatabase() {
        jdbc.update("DELETE FROM public.variant_image");
        jdbc.update("DELETE FROM public.product_variant");
        jdbc.update("DELETE FROM public.product");
        jdbc.update("DELETE FROM public.category");
        jdbc.update("DELETE FROM public.brand");
    }

    @Test
    void adminCreatesReadsReplacesAndDeletesCategoriesAndBrands() throws Exception {
        JsonNode category = create("/api/v1/admin/categories",
                "{\"name\":\" Tops \",\"slug\":\"tops\",\"is_active\":true}");
        UUID categoryId = UUID.fromString(category.path("id").asText());
        assertThat(category.path("name").asText()).isEqualTo("Tops");
        assertThat(category.path("slug").asText()).isEqualTo("tops");

        mockMvc.perform(get("/api/v1/categories").header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(categoryId.toString()))
                .andExpect(jsonPath("$.items[0].is_active").value(true));
        mockMvc.perform(get("/api/v1/categories/{id}", categoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tops"));
        mockMvc.perform(put("/api/v1/admin/categories/{id}", categoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Outerwear\",\"slug\":\"outerwear\",\"is_active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("outerwear"))
                .andExpect(jsonPath("$.is_active").value(false));
        mockMvc.perform(delete("/api/v1/admin/categories/{id}", categoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isNoContent());

        JsonNode brand = create("/api/v1/admin/brands",
                "{\"name\":\"StyleMind\",\"slug\":\"stylemind\",\"is_active\":true}");
        UUID brandId = UUID.fromString(brand.path("id").asText());
        mockMvc.perform(get("/api/v1/brands/{id}", brandId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("stylemind"));
        mockMvc.perform(delete("/api/v1/admin/brands/{id}", brandId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isNoContent());
    }

    @Test
    void adminRoutesRequireAdminRoleBeforeCallingCrud() throws Exception {
        mockMvc.perform(post("/api/v1/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tops\",\"slug\":\"tops\",\"is_active\":true}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(post("/api/v1/admin/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tops\",\"slug\":\"tops\",\"is_active\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.category", Integer.class)).isZero();
    }

    @Test
    void duplicateSlugsInvalidInputAndUnknownRecordsReturnProblemJson() throws Exception {
        UUID id = UUID.randomUUID();
        create("/api/v1/admin/categories", "{\"name\":\"Tops\",\"slug\":\"tops\",\"is_active\":true}");
        create("/api/v1/admin/brands", "{\"name\":\"Label\",\"slug\":\"label\",\"is_active\":true}");

        mockMvc.perform(post("/api/v1/admin/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Other\",\"slug\":\"tops\",\"is_active\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SLUG"));
        mockMvc.perform(post("/api/v1/admin/brands")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Other\",\"slug\":\"label\",\"is_active\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SLUG"));
        mockMvc.perform(post("/api/v1/admin/brands")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad\",\"slug\":\"bad--slug\",\"is_active\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(post("/api/v1/admin/brands")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad\",\"slug\":\"bad\",\"is_active\":true,\"extra\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/v1/categories/not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(put("/api/v1/admin/categories/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Missing\",\"slug\":\"missing\",\"is_active\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void referencedCategoryAndBrandCannotBeDeleted() throws Exception {
        JsonNode category = create("/api/v1/admin/categories",
                "{\"name\":\"Dresses\",\"slug\":\"dresses\",\"is_active\":true}");
        JsonNode brand = create("/api/v1/admin/brands",
                "{\"name\":\"Label\",\"slug\":\"label\",\"is_active\":true}");
        UUID categoryId = UUID.fromString(category.path("id").asText());
        UUID brandId = UUID.fromString(brand.path("id").asText());
        jdbc.update("""
                INSERT INTO public.product (
                    product_id, category_id, brand_id, name, product_status
                ) VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), categoryId, brandId, "Referenced product", "ACTIVE");

        mockMvc.perform(delete("/api/v1/admin/categories/{id}", categoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_REFERENCE_IN_USE"));
        mockMvc.perform(delete("/api/v1/admin/brands/{id}", brandId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_REFERENCE_IN_USE"));
    }

    @Test
    void openApiDocumentsCatalogRoutesAndSwaggerUiIsPublic() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode paths = objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("paths");
        assertThat(paths.path("/api/v1/categories").has("get")).isTrue();
        assertThat(paths.path("/api/v1/admin/categories").has("post")).isTrue();
        assertThat(paths.path("/api/v1/admin/categories/{categoryId}").has("put")).isTrue();
        assertThat(paths.path("/api/v1/admin/categories/{categoryId}").has("delete")).isTrue();
        assertThat(paths.path("/api/v1/brands").has("get")).isTrue();
        assertThat(paths.path("/api/v1/admin/brands").has("post")).isTrue();
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    private JsonNode create(String path, String body) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.startsWith("/api/v1/")))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
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
