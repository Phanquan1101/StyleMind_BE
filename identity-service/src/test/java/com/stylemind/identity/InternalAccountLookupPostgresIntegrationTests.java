package com.stylemind.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
class InternalAccountLookupPostgresIntegrationTests {

    private static final String PATH = "/internal/v1/accounts/batch-lookup";
    private static final String TOKEN_HEADER = "X-StyleMind-Service-Token";
    private static final String SERVICE_TOKEN = JwtTestSupport.randomSecret();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6")
            .withDatabaseName("stylemind_identity_internal_test")
            .withUsername("identity_internal_test")
            .withPassword("test-only-not-a-secret");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("stylemind.verification.hmac-secret", JwtTestSupport::randomSecret);
        registry.add("stylemind.notification.service-token", () -> SERVICE_TOKEN);
        registry.add("stylemind.security.service-token", () -> SERVICE_TOKEN);
        JwtTestSupport.registerProperties(registry);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    private UUID activeId;
    private UUID pendingId;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM public.profile_style");
        jdbc.update("DELETE FROM public.profile_color");
        jdbc.update("DELETE FROM public.profile_brand");
        jdbc.update("DELETE FROM public.style_profile");
        jdbc.update("DELETE FROM public.customer_address");
        jdbc.update("DELETE FROM public.user_profile");
        jdbc.update("DELETE FROM public.email_verification_challenge");
        jdbc.update("DELETE FROM public.password_reset_challenge");
        jdbc.update("DELETE FROM public.refresh_session");
        jdbc.update("DELETE FROM public.account_role");
        jdbc.update("DELETE FROM public.account");
        activeId = insertAccount("active-" + UUID.randomUUID() + "@example.test", "ACTIVE", "Linh Tran");
        pendingId = insertAccount("pending-" + UUID.randomUUID() + "@example.test", "PENDING_VERIFICATION", "Minh Le");
    }

    @Test
    void missingOrWrongTokenReturns401() throws Exception {
        String body = body(activeId);
        perform(post(PATH), body).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVICE_UNAUTHORIZED"));
        perform(post(PATH).header(TOKEN_HEADER, "wrong-service-token-value-that-is-invalid-xx"), body)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVICE_UNAUTHORIZED"));
    }

    @Test
    void returnsExactlyTheFourAccountFields() throws Exception {
        JsonNode items = items(body(activeId, pendingId));

        assertThat(items).hasSize(2);
        JsonNode active = find(items, activeId);
        assertThat(active.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("account_id", "email", "display_name", "account_status");
        assertThat(active.path("email").asText()).startsWith("active-").endsWith("@example.test");
        assertThat(active.path("display_name").asText()).isEqualTo("Linh Tran");
        assertThat(active.path("account_status").asText()).isEqualTo("ACTIVE");
        assertThat(find(items, pendingId).path("account_status").asText()).isEqualTo("PENDING_VERIFICATION");
    }

    @Test
    void unknownIdsAreSkippedAndDuplicatesCollapsed() throws Exception {
        JsonNode items = items(body(UUID.randomUUID(), activeId, activeId));

        assertThat(items).hasSize(1);
        assertThat(items.get(0).path("account_id").asText()).isEqualTo(activeId.toString());
        assertThat(items(body(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void invalidBodiesReturn400() throws Exception {
        String over = mapper.writeValueAsString(Map.of("account_ids",
                IntStream.range(0, 101).mapToObj(i -> UUID.randomUUID().toString()).toList()));
        for (String invalid : List.of(over, "{\"account_ids\":[]}", "{\"account_ids\":[\"not-a-uuid\"]}",
                "{\"account_ids\":[null]}", "{}", "{bad-json")) {
            internal(invalid).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        String hundred = mapper.writeValueAsString(Map.of("account_ids",
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
        return mapper.writeValueAsString(Map.of("account_ids", Arrays.stream(ids).map(UUID::toString).toList()));
    }

    private JsonNode find(JsonNode items, UUID accountId) {
        for (JsonNode item : items) {
            if (accountId.toString().equals(item.path("account_id").asText())) {
                return item;
            }
        }
        throw new AssertionError("Account not in response: " + accountId);
    }

    private UUID insertAccount(String email, String status, String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public.account(account_id, email, password_hash, account_status, registered_at)
                VALUES (?, ?, 'test-only-not-a-real-hash', ?, ?)
                """, id, email, status, OffsetDateTime.now());
        jdbc.update("INSERT INTO public.user_profile(account_id, display_name) VALUES (?, ?)", id, displayName);
        return id;
    }
}
