package com.stylemind.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.stylemind.commerce.model.order.AddressSnapshot;
import com.stylemind.commerce.service.order.IdentityAddressClient;
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
@Import(BranchCartOrderPostgresIntegrationTests.TestConfig.class)
class BranchCartOrderPostgresIntegrationTests {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6")
            .withDatabaseName("stylemind_commerce_bco_test")
            .withUsername("commerce_bco_test")
            .withPassword("test-only-not-a-secret");
    private static final RSAKey SIGNING_KEY = generateRsaKey();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID OTHER_CUSTOMER_ID = UUID.randomUUID();
    private static final UUID ADDRESS_ID = UUID.randomUUID();

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

    private UUID variantId;
    private UUID inactiveVariantId;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM public.cancellation_request");
        jdbc.update("DELETE FROM public.order_status_history");
        jdbc.update("DELETE FROM public.order_item");
        jdbc.update("DELETE FROM public.order_address_snapshot");
        jdbc.update("DELETE FROM public.customer_order");
        jdbc.update("DELETE FROM public.cart_item");
        jdbc.update("DELETE FROM public.cart");
        jdbc.update("DELETE FROM public.variant_image");
        jdbc.update("DELETE FROM public.product_variant");
        jdbc.update("DELETE FROM public.product");
        jdbc.update("DELETE FROM public.branch");
        jdbc.update("DELETE FROM public.category");
        jdbc.update("DELETE FROM public.brand");
        UUID categoryId = UUID.randomUUID();
        UUID brandId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        inactiveVariantId = UUID.randomUUID();
        jdbc.update("INSERT INTO public.category(category_id, name, slug, is_active) VALUES (?, 'Tops', 'tops', true)",
                categoryId);
        jdbc.update("INSERT INTO public.brand(brand_id, name, slug, is_active) VALUES (?, 'StyleMind', 'stylemind', true)",
                brandId);
        jdbc.update("""
                INSERT INTO public.product(product_id, category_id, brand_id, name, product_status)
                VALUES (?, ?, ?, 'Linen Shirt', 'ACTIVE')
                """, productId, categoryId, brandId);
        jdbc.update("""
                INSERT INTO public.product_variant(variant_id, product_id, sku, color, size, unit_price_vnd, variant_status)
                VALUES (?, ?, 'SKU-A', 'White', 'M', 100000, 'ACTIVE')
                """, variantId, productId);
        jdbc.update("""
                INSERT INTO public.product_variant(variant_id, product_id, sku, unit_price_vnd, variant_status)
                VALUES (?, ?, 'SKU-X', 90000, 'INACTIVE')
                """, inactiveVariantId, productId);
    }

    @Test
    void branchCrudRejectsDuplicateCodeHidesInactiveAndRequiresAdmin() throws Exception {
        assertProblem(postJson("/api/v1/admin/branches", customerToken(), branchBody("HN01", "ACTIVE")),
                403, "ACCESS_DENIED");
        JsonNode created = json(postJson("/api/v1/admin/branches", adminToken(), branchBody("HN01", "ACTIVE")));
        UUID branchId = UUID.fromString(created.path("branch_id").asText());
        assertProblem(postJson("/api/v1/admin/branches", adminToken(), branchBody("HN01", "ACTIVE")),
                409, "DUPLICATE_BRANCH_CODE");
        mockMvc.perform(put("/api/v1/admin/branches/{id}", branchId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchBody("HN01", "INACTIVE")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/branches/{id}", branchId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/admin/branches/{id}", branchId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branch_status").value("INACTIVE"));
    }

    @Test
    void cartCrudValidatesOwnerQuantityActiveVariantsAndTotals() throws Exception {
        JsonNode cart = json(postJson("/api/v1/cart/items", null, """
                {"variant_id":"%s","quantity":2,"guest_session_key":"guest-session-0001"}
                """.formatted(variantId)));
        UUID itemId = UUID.fromString(cart.path("items").get(0).path("cart_item_id").asText());
        assertThat(cart.path("total_vnd").asLong()).isEqualTo(200000);
        assertProblem(postJson("/api/v1/cart/items", null, """
                {"variant_id":"%s","quantity":1,"guest_session_key":"guest-session-0001"}
                """.formatted(inactiveVariantId)), 409, "VARIANT_NOT_ACTIVE");
        mockMvc.perform(put("/api/v1/cart/items/{id}", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3,\"guest_session_key\":\"guest-session-0001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_vnd").value(300000));
        mockMvc.perform(delete("/api/v1/cart/items/{id}", itemId)
                        .queryParam("guest_session_key", "other-guest-session-0001"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/cart/items")
                        .queryParam("guest_session_key", "guest-session-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void mergeCombinesDuplicateVariantsAndRollsBackInvalidGuestCart() throws Exception {
        postJson("/api/v1/cart/items", null, """
                {"variant_id":"%s","quantity":1,"guest_session_key":"guest-session-0002"}
                """.formatted(variantId));
        postJson("/api/v1/cart/items", customerToken(), """
                {"variant_id":"%s","quantity":2,"guest_session_key":null}
                """.formatted(variantId));
        mockMvc.perform(post("/api/v1/cart/merge")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guest_session_key\":\"guest-session-0002\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.total_vnd").value(300000));
        mockMvc.perform(post("/api/v1/cart/merge")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guest_session_key\":\"guest-session-0002\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(3));

        UUID badCart = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public.cart(cart_id, guest_session_key, cart_status, created_at, updated_at)
                VALUES (?, 'guest-session-0003', 'ACTIVE', now(), now())
                """, badCart);
        jdbc.update("""
                INSERT INTO public.cart_item(cart_item_id, cart_id, variant_id, quantity, last_seen_price_vnd, added_at, updated_at)
                VALUES (?, ?, ?, 1, 90000, now(), now())
                """, UUID.randomUUID(), badCart, inactiveVariantId);
        assertProblem(mockMvc.perform(post("/api/v1/cart/merge")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guest_session_key\":\"guest-session-0003\"}"))
                .andReturn(), 409, "INVALID_CART_ITEM");
        Integer activeGuest = jdbc.queryForObject(
                "SELECT count(*) FROM public.cart WHERE guest_session_key = 'guest-session-0003' AND cart_status = 'ACTIVE'",
                Integer.class);
        assertThat(activeGuest).isEqualTo(1);
    }

    @Test
    void checkoutSnapshotsPricesPreventsRetryAndOrderStatusAndCancellationAreGuarded() throws Exception {
        postJson("/api/v1/cart/items", customerToken(), """
                {"variant_id":"%s","quantity":2,"guest_session_key":null}
                """.formatted(variantId));
        jdbc.update("UPDATE public.product_variant SET unit_price_vnd = 120000 WHERE variant_id = ?", variantId);
        JsonNode order = json(postJson("/api/v1/orders", customerToken(), """
                {"checkout_key":"checkout-key-0001","shipping_address_id":"%s","payment_method":"COD"}
                """.formatted(ADDRESS_ID)));
        UUID orderId = UUID.fromString(order.path("order_id").asText());
        assertThat(order.path("accepted_total_vnd").asLong()).isEqualTo(240000);
        assertThat(order.path("address").path("recipient_name").asText()).isEqualTo("Nguyen Van A");
        assertProblem(postJson("/api/v1/orders", customerToken(), """
                {"checkout_key":"checkout-key-0001","shipping_address_id":"%s","payment_method":"COD"}
                """.formatted(ADDRESS_ID)), 409, "DUPLICATE_CHECKOUT");
        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherCustomerToken())))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/admin/orders/{id}/status", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"order_status\":\"COMPLETED\",\"reason\":null}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS_TRANSITION"));
        JsonNode cancellation = json(postJson("/api/v1/orders/" + orderId + "/cancellation-requests",
                customerToken(), "{\"reason\":\"Customer changed delivery plan\"}"));
        mockMvc.perform(put("/api/v1/admin/cancellation-requests/{id}",
                        cancellation.path("cancellation_request_id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request_status\":\"APPROVED\",\"decision_reason\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request_status").value("APPROVED"));
        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order_status").value("CANCELLED"))
                .andExpect(jsonPath("$.history.length()").value(2));
    }

    private String branchBody(String code, String status) {
        return """
                {"branch_code":"%s","name":"Ha Noi","address_line":"1 Main St","contact_phone":null,"branch_status":"%s"}
                """.formatted(code, status);
    }

    private MvcResult postJson(String path, String token, String body) throws Exception {
        var builder = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        return mockMvc.perform(builder).andReturn();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private void assertProblem(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(json(result).path("code").asText()).isEqualTo(code);
    }

    private String adminToken() {
        return token(UUID.randomUUID(), "ADMIN");
    }

    private String customerToken() {
        return token(CUSTOMER_ID, "CUSTOMER");
    }

    private String otherCustomerToken() {
        return token(OTHER_CUSTOMER_ID, "CUSTOMER");
    }

    private String token(UUID subject, String role) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject.toString())
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
    static class TestConfig {

        @Bean
        @Primary
        JwtDecoder testJwtDecoder() throws Exception {
            return NimbusJwtDecoder.withPublicKey(SIGNING_KEY.toRSAPublicKey()).build();
        }

        @Bean
        @Primary
        IdentityAddressClient identityAddressClient() {
            return new IdentityAddressClient(null) {
                @Override
                public AddressSnapshot getAddress(UUID addressId, String authorizationHeader) {
                    if (!ADDRESS_ID.equals(addressId)) {
                        throw new com.stylemind.commerce.errors.ApiOperationException(
                                org.springframework.http.HttpStatus.CONFLICT, "INVALID_SHIPPING_ADDRESS",
                                "Shipping address does not exist for this customer");
                    }
                    return new AddressSnapshot(addressId, "Nguyen Van A", "+84901234567",
                            "1 Nguyen Trai, Thanh Xuan, Ha Noi");
                }
            };
        }
    }
}
