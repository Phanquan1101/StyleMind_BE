package com.stylemind.stylist.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.stylemind.stylist.StylistServiceApplication;
import com.stylemind.stylist.client.commerce.CommerceCatalogClient;
import com.stylemind.stylist.client.commerce.CommerceCatalogClient.HydratedVariant;
import com.stylemind.stylist.entity.message.StylistMessageEntity;
import com.stylemind.stylist.entity.recommendation.RecommendationItemEntity;
import com.stylemind.stylist.dto.request.CreateStylingRequestRequest;
import com.stylemind.stylist.entity.constraint.StylingConstraintEntity;
import com.stylemind.stylist.entity.request.StylingRequestEntity;
import com.stylemind.stylist.entity.session.StylistSessionEntity;
import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import com.stylemind.stylist.repository.constraint.StylingConstraintRepository;
import com.stylemind.stylist.repository.message.StylistMessageRepository;
import com.stylemind.stylist.repository.request.StylingRequestRepository;
import com.stylemind.stylist.repository.recommendation.RecommendationItemRepository;
import com.stylemind.stylist.repository.recommendation.WardrobeRecommendationRepository;
import com.stylemind.stylist.repository.session.StylistSessionRepository;
import com.stylemind.stylist.security.JwtContractValidator;
import com.stylemind.stylist.security.SessionRequestPolicyFilter;
import com.stylemind.stylist.service.request.StylingRequestParser;
import com.stylemind.stylist.service.request.StylingRequestService;
import com.stylemind.stylist.service.generation.GenerationPersistenceService;
import com.stylemind.stylist.model.generation.GenerationModel.Claim;
import com.stylemind.stylist.model.generation.GenerationModel.PersistedItem;
import com.stylemind.stylist.model.generation.GenerationModel.PersistedRecommendation;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = StylistServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.health.db.enabled=false",
        "management.health.neo4j.enabled=false",
        "spring.neo4j.uri=bolt://127.0.0.1:1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "stylemind.security.jwt.jwk-set-uri=",
        "stylemind.security.jwt.jws-algorithm=RS256",
        "stylemind.security.jwt.issuer=https://identity.stylemind.local",
        "stylemind.security.jwt.audience=stylemind-api",
        "stylemind.stylist.knowledge.enabled=false",
        "stylemind.stylist.knowledge.neo4j-uri=bolt://127.0.0.1:1",
        "stylemind.openai.enabled=false"
})
@AutoConfigureMockMvc
@Import(StylistSessionApiIntegrationTests.TestJwtConfiguration.class)
class StylistSessionApiIntegrationTests {

    private static final String ISSUER = "https://identity.stylemind.local";
    private static final String AUDIENCE = "stylemind-api";
    private static final String API = "/api/v1/stylist/sessions";
    private static final RSAKey SIGNING_KEY = generateRsaKey();
    private static final RSAKey OTHER_KEY = generateRsaKey();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("stylemind_stylist_test")
            .withUsername("stylist_test")
            .withPassword("stylist_test_only");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @LocalServerPort int localServerPort;
    @Autowired ObjectMapper objectMapper;
    @Autowired StylistSessionRepository sessionRepository;
    @Autowired StylistMessageRepository messageRepository;
    @Autowired RecommendationItemRepository recommendationItemRepository;
    @Autowired WardrobeRecommendationRepository recommendationRepository;
    @Autowired GenerationPersistenceService generationPersistence;
    @SpyBean CommerceCatalogClient commerceCatalogClient;
    @SpyBean StylingRequestRepository stylingRequestRepository;
    @SpyBean StylingConstraintRepository stylingConstraintRepository;
    @SpyBean StylingRequestService stylingRequestService;
    @Autowired StylingRequestParser stylingRequestParser;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired Flyway flyway;
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearDisposableDatabase() {
        recommendationItemRepository.deleteAllInBatch();
        recommendationRepository.deleteAllInBatch();
        stylingConstraintRepository.deleteAllInBatch();
        stylingRequestRepository.deleteAllInBatch();
        messageRepository.deleteAllInBatch();
        sessionRepository.deleteAllInBatch();
    }

    @Test
    void generationConfigurationGateDoesNotClaimRequestAndUserErrorIsNoStore() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID sessionId = createSession(owner, List.of("CUSTOMER"));
        UUID requestId = createRequest(owner, sessionId, "{\"message\":\"tìm áo và quần cho đi làm\"}");
        String authorization = bearer(token(owner, List.of("CUSTOMER")));

        mockMvc.perform(post("/api/v1/stylist/requests/{requestId}/generate", requestId)
                        .header("Authorization", authorization).contentType("application/json").content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GENERATION_RUNTIME_UNCONFIGURED"))
                .andExpect(jsonPath("$.request_id").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"));
        mockMvc.perform(get("/api/v1/stylist/requests/{requestId}", requestId).header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request_status").value("RECEIVED"))
                .andExpect(jsonPath("$.generation_started").value(false))
                .andExpect(header().string("Cache-Control", "no-store"));
        mockMvc.perform(post("/api/v1/stylist/requests/{requestId}/generate", requestId)
                        .header("Authorization", authorization).contentType("application/json").content("{} {}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(header().string("Cache-Control", "no-store"));
        assertThat(stylingRequestRepository.findById(requestId).orElseThrow().getRequestStatus())
                .isEqualTo("RECEIVED");
    }

    @Test
    void generationClaimHasOneConcurrentWinnerAndRechecksClosedSessionAndOwner() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID requestId = createRequest(owner, createSession(owner, List.of("CUSTOMER")), "{\"occasion\":\"work\"}");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> concurrentClaim(owner, requestId, ready, start));
            var second = executor.submit(() -> concurrentClaim(owner, requestId, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            Claim a = first.get(10, TimeUnit.SECONDS);
            Claim b = second.get(10, TimeUnit.SECONDS);
            assertThat(List.of(a, b).stream().filter(Claim::acquired).count()).isEqualTo(1);
            assertThat(List.of(a, b)).allSatisfy(claim -> assertThat(claim.status()).isEqualTo("PROCESSING"));
        } finally {
            executor.shutdownNow();
        }

        UUID closedOwner = UUID.randomUUID();
        UUID closedSession = createSession(closedOwner, List.of("CUSTOMER"));
        UUID closedRequest = createRequest(closedOwner, closedSession, "{\"occasion\":\"work\"}");
        jdbcTemplate.update("UPDATE public.stylist_session SET closed_at = opened_at + INTERVAL '1 second' WHERE stylist_session_id = ?",
                closedSession);
        assertThatThrownBy(() -> generationPersistence.claim(closedOwner, closedRequest))
                .satisfies(failure -> assertThat(((StylistApiException) failure)
                        .errorCode()).isEqualTo(ErrorCode.SESSION_CLOSED));
        assertThat(stylingRequestRepository.findById(closedRequest).orElseThrow().getRequestStatus())
                .isEqualTo("RECEIVED");
        assertThatThrownBy(() -> generationPersistence.claim(UUID.randomUUID(), closedRequest))
                .satisfies(failure -> assertThat(((StylistApiException) failure)
                        .errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void finalizeRollbackLeavesNoRecommendationRowsAndFailureCannotOverwriteSuccess() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID requestId = createRequest(owner, createSession(owner, List.of("CUSTOMER")), "{\"occasion\":\"work\"}");
        assertThat(generationPersistence.claim(owner, requestId).acquired()).isTrue();
        jdbcTemplate.execute("""
                CREATE FUNCTION public.p07_fail_item_insert() RETURNS trigger AS $$
                BEGIN RAISE EXCEPTION 'test-only recommendation item failure'; END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER p07_fail_item_insert BEFORE INSERT ON public.recommendation_item
                FOR EACH ROW EXECUTE FUNCTION public.p07_fail_item_insert()
                """);
        try {
            assertThatThrownBy(() -> generationPersistence.complete(owner, requestId, "SUCCEEDED",
                    List.of(persistedRecommendation(UUID.randomUUID())))).isInstanceOf(RuntimeException.class);
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS p07_fail_item_insert ON public.recommendation_item");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS public.p07_fail_item_insert()");
        }
        assertThat(stylingRequestRepository.findById(requestId).orElseThrow().getRequestStatus())
                .isEqualTo("PROCESSING");
        assertThat(recommendationRepository.count()).isZero();
        assertThat(recommendationItemRepository.count()).isZero();

        assertThat(generationPersistence.complete(owner, requestId, "SUCCEEDED",
                List.of(persistedRecommendation(UUID.randomUUID())))).hasSize(1);
        generationPersistence.markFailed(owner, requestId);
        assertThat(stylingRequestRepository.findById(requestId).orElseThrow().getRequestStatus())
                .isEqualTo("SUCCEEDED");
        assertThat(recommendationRepository.count()).isEqualTo(1);
        assertThat(recommendationItemRepository.count()).isEqualTo(2);
    }

    @Test
    void recommendationReadsAreOwnerScopedAndCurrentDetailsAreExplicitlyNotHistorical() throws Exception {
        UUID ownerA = UUID.randomUUID();
        UUID requestId = createRequest(ownerA, createSession(ownerA, List.of("CUSTOMER")), "{\"occasion\":\"work\"}");
        generationPersistence.claim(ownerA, requestId);
        UUID topVariant = UUID.randomUUID();
        UUID bottomVariant = UUID.randomUUID();
        UUID recommendationId = UUID.randomUUID();
        UUID brand = UUID.randomUUID();
        generationPersistence.complete(ownerA, requestId, "SUCCEEDED", List.of(
                new PersistedRecommendation(recommendationId, "Reviewable explanation", "Layer lightly",
                        Instant.now(), List.of(new PersistedItem(topVariant, "TOP", "top reason", 1),
                        new PersistedItem(bottomVariant, "BOTTOM", "bottom reason", 2)))));

        mockMvc.perform(get("/api/v1/stylist/requests/{requestId}/recommendations", requestId)
                        .header("Authorization", bearer(token(ownerA, List.of("CUSTOMER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request_status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.recommendations[0].items[0].slot_name").value("TOP"))
                .andExpect(jsonPath("$.recommendations[0].items[1].slot_name").value("BOTTOM"))
                .andExpect(header().string("Cache-Control", "no-store"));
        UUID ownerB = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/stylist/requests/{requestId}/recommendations", requestId)
                        .header("Authorization", bearer(token(ownerB, List.of("CUSTOMER")))))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store"));
        mockMvc.perform(get("/api/v1/stylist/recommendations/{recommendationId}", recommendationId)
                        .header("Authorization", bearer(token(ownerB, List.of("ADMIN")))))
                .andExpect(status().isNotFound());

        UUID topProduct = UUID.randomUUID();
        UUID bottomProduct = UUID.randomUUID();
        UUID topCategory = UUID.randomUUID();
        UUID bottomCategory = UUID.randomUUID();
        doReturn(Map.of(
                topVariant, new HydratedVariant(topProduct, topVariant, topCategory, brand, "Blouse", "Top",
                        "StyleMind", "WOMEN", "ACTIVE", "ACTIVE", "White", "M", "Cotton", 120_000, List.of()),
                bottomVariant, new HydratedVariant(bottomProduct, bottomVariant, bottomCategory, brand, "Trousers",
                        "Bottom", "StyleMind", "WOMEN", "ACTIVE", "ACTIVE", "Black", "M", "Denim", 180_000, List.of())))
                .when(commerceCatalogClient).hydrateCurrentVariants(List.of(topVariant, bottomVariant));
        mockMvc.perform(get("/api/v1/stylist/recommendations/{recommendationId}", recommendationId)
                        .header("Authorization", bearer(token(ownerA, List.of("CUSTOMER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_current_price_vnd").value(300000))
                .andExpect(jsonPath("$.price_semantics").value("CURRENT_COMMERCE_DATA_NOT_HISTORICAL"))
                .andExpect(jsonPath("$.items[0].current_unit_price_vnd").value(120000))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void nullCurrentCommercePriceReturnsSanitizedBadGatewayProblemDetails() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID requestId = createRequest(owner, createSession(owner, List.of("CUSTOMER")), "{\"occasion\":\"work\"}");
        assertThat(generationPersistence.claim(owner, requestId).acquired()).isTrue();
        UUID recommendationId = UUID.randomUUID();
        UUID topVariant = UUID.randomUUID();
        UUID bottomVariant = UUID.randomUUID();
        generationPersistence.complete(owner, requestId, "SUCCEEDED", List.of(
                new PersistedRecommendation(recommendationId, "A grounded outfit", null, Instant.now(), List.of(
                        new PersistedItem(topVariant, "TOP", "Top reason", 1),
                        new PersistedItem(bottomVariant, "BOTTOM", "Bottom reason", 2)))));
        doThrow(new StylistApiException(ErrorCode.DEPENDENCY_PROTOCOL_ERROR))
                .when(commerceCatalogClient).hydrateCurrentVariants(List.of(topVariant, bottomVariant));

        mockMvc.perform(get("/api/v1/stylist/recommendations/{recommendationId}", recommendationId)
                        .header("Authorization", bearer(token(owner, List.of("CUSTOMER"))))
                        .header("X-Correlation-Id", "null-price-detail"))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.code").value("DEPENDENCY_PROTOCOL_ERROR"))
                .andExpect(jsonPath("$.detail").value("A dependency returned an invalid response"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Correlation-Id", "null-price-detail"));
    }

    @Test
    void createsOwnedEmptySessionWithNoStoreAndGatewayLocation() throws Exception {
        UUID owner = UUID.randomUUID();
        MvcResult result = mockMvc.perform(post(API)
                        .header("Authorization", bearer(token(owner, List.of("CUSTOMER"))))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(
                        "/stylist/api/v1/stylist/sessions/[0-9a-fA-F-]{36}")))
                .andExpect(jsonPath("$.session_id").isNotEmpty())
                .andExpect(jsonPath("$.title").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.closed_at").value(org.hamcrest.Matchers.nullValue()))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        UUID sessionId = UUID.fromString(body.path("session_id").asText());
        StylistSessionEntity stored = sessionRepository.findByStylistSessionIdAndCustomerId(sessionId, owner)
                .orElseThrow();
        assertThat(stored.getCustomerId()).isEqualTo(owner);
        assertThat(stored.getOpenedAt()).isNotNull();
        assertThat(stored.getClosedAt()).isNull();
        assertThat(messageRepository.count()).isZero();
    }

    @Test
    void omittedAndNullTitleAreBothStoredAsAbsentAndStringTitleIsTrimmed() throws Exception {
        UUID owner = UUID.randomUUID();
        String auth = bearer(token(owner, List.of("CUSTOMER")));
        for (String requestBody : List.of("{}", "{\"title\":null}", "{\"title\":\"  work  \"}")) {
            MvcResult result = mockMvc.perform(post(API).header("Authorization", auth)
                            .contentType("application/json").content(requestBody))
                    .andExpect(status().isCreated()).andReturn();
            JsonNode json = objectMapper.readTree(result.getResponse().getContentAsByteArray());
            if (requestBody.contains("work")) assertThat(json.path("title").asText()).isEqualTo("work");
            else assertThat(json.path("title").isNull()).isTrue();
        }
        assertThat(sessionRepository.count()).isEqualTo(3);
    }

    @Test
    void titleUsesTrimmedUnicodeCodePointLengthAndRejectsControls() throws Exception {
        UUID owner = UUID.randomUUID();
        String auth = bearer(token(owner, List.of("CUSTOMER")));
        String hundredCodePoints = "😀".repeat(100);
        mockMvc.perform(post(API).header("Authorization", auth).contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(Map.of("title", hundredCodePoints))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value(hundredCodePoints));
        mockMvc.perform(post(API).header("Authorization", auth).contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(Map.of("title", "😀".repeat(101)))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(API).header("Authorization", auth).contentType("application/json")
                        .content("{\"title\":\"line\\nbreak\"}"))
                .andExpect(status().isBadRequest());
        assertThat(sessionRepository.count()).isEqualTo(1);
    }

    @Test
    void strictRequestRejectsUnknownDuplicateWrongTypeAndNonObjectJson() throws Exception {
        UUID owner = UUID.randomUUID();
        String auth = bearer(token(owner, List.of("CUSTOMER")));
        for (String body : List.of(
                "{\"customer_id\":\"" + owner + "\"}",
                "{\"unknown\":true}",
                "{\"title\":\"a\",\"title\":\"b\"}",
                "{} {}",
                "{\"title\":7}",
                "[]",
                "null",
                "{broken}")) {
            mockMvc.perform(post(API).header("Authorization", auth).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(header().string("Cache-Control", "no-store"));
        }
        assertThat(sessionRepository.count()).isZero();
    }

    @Test
    void bodyLimitAllowsExactly32KiBAndRejectsOneByteMore() throws Exception {
        UUID owner = UUID.randomUUID();
        String auth = bearer(token(owner, List.of("CUSTOMER")));
        String prefix = "{\"title\":\"ok\"}";
        String exact = prefix + " ".repeat(32 * 1024 - prefix.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        mockMvc.perform(post(API).header("Authorization", auth).contentType("application/json").content(exact))
                .andExpect(status().isCreated());
        String oversized = exact + " ";
        mockMvc.perform(post(API).header("Authorization", auth).contentType("application/json").content(oversized))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
                .andExpect(header().string("Cache-Control", "no-store"));
        assertThat(sessionRepository.count()).isEqualTo(1);
    }

    @Test
    void unsupportedMediaTypeReturns415() throws Exception {
        mockMvc.perform(post(API).header("Authorization", bearer(token(UUID.randomUUID(), List.of("CUSTOMER"))))
                        .contentType("text/plain").content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void jwtRequiresSignatureIssuerAudienceUuidSubjectAndSupportedAlgorithm() throws Exception {
        UUID owner = UUID.randomUUID();
        String path = API;
        mockMvc.perform(post(path).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        List<String> invalidTokens = List.of(
                token(owner, List.of("CUSTOMER"), ISSUER, AUDIENCE, Instant.now().minusSeconds(120), SIGNING_KEY,
                        SignatureAlgorithm.RS256),
                token(owner, List.of("CUSTOMER"), "https://wrong.stylemind.local", AUDIENCE,
                        Instant.now().plusSeconds(300), SIGNING_KEY, SignatureAlgorithm.RS256),
                token(owner, List.of("CUSTOMER"), ISSUER, "other-api", Instant.now().plusSeconds(300), SIGNING_KEY,
                        SignatureAlgorithm.RS256),
                token("not-a-uuid", List.of("CUSTOMER"), ISSUER, AUDIENCE, Instant.now().plusSeconds(300),
                        SIGNING_KEY, SignatureAlgorithm.RS256),
                token(owner, List.of("CUSTOMER"), ISSUER, AUDIENCE, Instant.now().plusSeconds(300), OTHER_KEY,
                        SignatureAlgorithm.RS256),
                token(owner, List.of("CUSTOMER"), ISSUER, AUDIENCE, Instant.now().plusSeconds(300), SIGNING_KEY,
                        SignatureAlgorithm.RS384));
        for (String invalidToken : invalidTokens) {
            mockMvc.perform(post(path).header("Authorization", bearer(invalidToken))
                            .contentType("application/json").content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_BEARER_TOKEN"));
        }
        assertThat(sessionRepository.count()).isZero();
    }

    @Test
    void unsupportedRoleIsForbiddenAndAdminCanOnlyUseTheirOwnSession() throws Exception {
        UUID admin = UUID.randomUUID();
        UUID customer = UUID.randomUUID();
        mockMvc.perform(post(API).header("Authorization", bearer(token(customer, List.of("SERVICE"))))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        UUID sessionId = createSession(admin, List.of("ADMIN"));
        mockMvc.perform(get(API + "/" + sessionId + "/messages")
                        .header("Authorization", bearer(token(customer, List.of("ADMIN")))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get(API + "/" + UUID.randomUUID() + "/messages")
                        .header("Authorization", bearer(token(customer, List.of("CUSTOMER")))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Resource was not found"));
    }

    @Test
    void foreignAndMissingSessionHaveSameNotFoundProblem() throws Exception {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        UUID sessionA = createSession(ownerA, List.of("CUSTOMER"));
        String authB = bearer(token(ownerB, List.of("CUSTOMER")));
        MvcResult foreign = mockMvc.perform(get(API + "/" + sessionA + "/messages").header("Authorization", authB))
                .andExpect(status().isNotFound()).andReturn();
        MvcResult missing = mockMvc.perform(get(API + "/" + UUID.randomUUID() + "/messages")
                        .header("Authorization", authB))
                .andExpect(status().isNotFound()).andReturn();
        JsonNode foreignBody = objectMapper.readTree(foreign.getResponse().getContentAsByteArray());
        JsonNode missingBody = objectMapper.readTree(missing.getResponse().getContentAsByteArray());
        assertThat(foreignBody.path("code").asText()).isEqualTo(missingBody.path("code").asText());
        assertThat(foreignBody.path("detail").asText()).isEqualTo(missingBody.path("detail").asText());
        assertThat(foreign.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void newlyCreatedSessionReturnsEmptyHistoryAndGetNeverWrites() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID sessionId = createSession(owner, List.of("CUSTOMER"));
        long sessionsBefore = sessionRepository.count();
        long messagesBefore = messageRepository.count();
        MvcResult result = mockMvc.perform(get(API + "/" + sessionId + "/messages")
                        .header("Authorization", bearer(token(owner, List.of("CUSTOMER")))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items").isArray())
                .andReturn();
        JsonNode page = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(page.path("items").isEmpty()).isTrue();
        assertThat(page.path("next_cursor").isNull()).isTrue();
        assertThat(sessionRepository.count()).isEqualTo(sessionsBefore);
        assertThat(messageRepository.count()).isEqualTo(messagesBefore);
    }

    @Test
    void queryValidationRejectsBadUuidLimitsUnknownDuplicateAndBody() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID sessionId = createSession(owner, List.of("CUSTOMER"));
        String auth = bearer(token(owner, List.of("CUSTOMER")));
        for (String uri : List.of(
                API + "/not-a-uuid/messages",
                API + "/" + sessionId + "/messages?limit=0",
                API + "/" + sessionId + "/messages?limit=51",
                API + "/" + sessionId + "/messages?limit=1&limit=2",
                API + "/" + sessionId + "/messages?other=x")) {
            mockMvc.perform(get(uri).header("Authorization", auth))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        mockMvc.perform(get(API + "/" + sessionId + "/messages")
                        .header("Authorization", auth).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void keysetPaginationIsStableForEqualTimestampsAndUuidTieBreak() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID sessionId = createSession(owner, List.of("CUSTOMER"));
        Instant sentAt = Instant.parse("2026-10-29T12:30:00.123456Z");
        for (long id = 1; id <= 5; id++) {
            messageRepository.save(new StylistMessageEntity(new UUID(0, id), sessionId,
                    id % 2 == 0 ? "ASSISTANT" : "USER", "synthetic-message-" + id, sentAt));
        }
        String auth = bearer(token(owner, List.of("CUSTOMER")));
        List<Long> observed = new ArrayList<>();
        String cursor = null;
        for (int pageNumber = 0; pageNumber < 3; pageNumber++) {
            var builder = get(API + "/" + sessionId + "/messages").param("limit", "2").header("Authorization", auth);
            if (cursor != null) builder.param("before", cursor);
            JsonNode page = objectMapper.readTree(mockMvc.perform(builder).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray());
            page.path("items").forEach(item -> observed.add(UUID.fromString(item.path("message_id").asText())
                    .getLeastSignificantBits()));
            cursor = page.path("next_cursor").isNull() ? null : page.path("next_cursor").asText();
            if (pageNumber < 2) assertThat(cursor).isNotBlank();
            else assertThat(cursor).isNull();
        }
        assertThat(observed).containsExactly(5L, 4L, 3L, 2L, 1L);
    }

    @Test
    void cursorIsOpaqueBoundToSessionAndOwnerAndMalformedCursorIsRejected() throws Exception {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        UUID sessionA = createSession(ownerA, List.of("CUSTOMER"));
        UUID sessionB = createSession(ownerB, List.of("CUSTOMER"));
        Instant sentAt = Instant.parse("2026-10-29T12:30:00Z");
        messageRepository.save(new StylistMessageEntity(new UUID(0, 1), sessionA, "USER", "one", sentAt));
        messageRepository.save(new StylistMessageEntity(new UUID(0, 2), sessionA, "ASSISTANT", "two", sentAt));
        JsonNode firstPage = objectMapper.readTree(mockMvc.perform(get(API + "/" + sessionA + "/messages")
                        .param("limit", "1")
                        .header("Authorization", bearer(token(ownerA, List.of("CUSTOMER")))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        String cursorA = firstPage.path("next_cursor").asText();
        assertThat(cursorA).isNotBlank();
        mockMvc.perform(get(API + "/" + sessionB + "/messages").param("before", cursorA)
                        .header("Authorization", bearer(token(ownerB, List.of("CUSTOMER")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        mockMvc.perform(get(API + "/" + sessionA + "/messages").param("before", "not-a-cursor")
                        .header("Authorization", bearer(token(ownerA, List.of("CUSTOMER")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }

    @Test
    void historyUsesTwoBoundedQueriesWithoutNPlusOne() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID sessionId = createSession(owner, List.of("CUSTOMER"));
        for (long id = 1; id <= 10; id++) {
            messageRepository.save(new StylistMessageEntity(new UUID(0, id), sessionId,
                    "USER", "synthetic-" + id, Instant.parse("2026-10-29T12:30:00Z").plusSeconds(id)));
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mockMvc.perform(get(API + "/" + sessionId + "/messages").param("limit", "5")
                        .header("Authorization", bearer(token(owner, List.of("CUSTOMER")))))
                .andExpect(status().isOk());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    void disposableFlywayMigrationsCreateOnlyApprovedTablesAndValidateOnRerun() {
        assertThat(flyway.info().applied()).hasSize(3);
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        List<String> tables = jdbcTemplate.queryForList("""
                SELECT tablename FROM pg_tables
                WHERE schemaname = 'public'
                  AND tablename IN ('stylist_message', 'stylist_session', 'styling_request', 'styling_constraint',
                                    'wardrobe_recommendation', 'recommendation_item')
                ORDER BY tablename
                """, String.class);
        assertThat(tables).containsExactly("recommendation_item", "styling_constraint", "styling_request",
                "stylist_message", "stylist_session", "wardrobe_recommendation");
        List<String> indexes = jdbcTemplate.queryForList("""
                SELECT indexname FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname IN ('idx_stylist_session_customer_session',
                                    'idx_stylist_message_session_sent_message',
                                    'idx_styling_request_session', 'idx_styling_constraint_request',
                                    'idx_wardrobe_recommendation_request_generated',
                                    'idx_recommendation_item_recommendation')
                """, String.class);
        assertThat(indexes).containsExactlyInAnyOrder(
                "idx_stylist_session_customer_session", "idx_stylist_message_session_sent_message",
                "idx_styling_request_session", "idx_styling_constraint_request",
                "idx_wardrobe_recommendation_request_generated", "idx_recommendation_item_recommendation");
    }

    @Test
    void createsAndReadsReceivedRequestWithAUserMessageAndConstraint() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID sessionId = createSession(owner, List.of("CUSTOMER"));
        MvcResult created = mockMvc.perform(post(API + "/" + sessionId + "/requests")
                        .header("Authorization", bearer(token(owner, List.of("CUSTOMER"))))
                        .contentType("application/json")
                        .content("""
                                {"message":"  I need a simple work outfit  ","occasion":"work",
                                 "style_context":"minimal","max_budget_vnd":850000,"budget_is_hard":true,
                                 "constraints":[{"constraint_kind":"EXCLUDE_COLOR","text_value":"RED","is_hard":true}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(
                        "/stylist/api/v1/stylist/requests/[0-9a-fA-F-]{36}")))
                .andExpect(jsonPath("$.request_status").value("RECEIVED"))
                .andExpect(jsonPath("$.generation_started").value(false))
                .andReturn();

        JsonNode createdBody = objectMapper.readTree(created.getResponse().getContentAsByteArray());
        UUID requestId = UUID.fromString(createdBody.path("request_id").asText());
        UUID messageId = UUID.fromString(createdBody.path("source_message_id").asText());
        assertThat(stylingRequestRepository.count()).isEqualTo(1);
        assertThat(stylingConstraintRepository.count()).isEqualTo(1);
        assertThat(messageRepository.findById(messageId).orElseThrow().getContent())
                .isEqualTo("I need a simple work outfit");

        mockMvc.perform(get("/api/v1/stylist/requests/" + requestId)
                        .header("Authorization", bearer(token(owner, List.of("CUSTOMER")))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.request_id").value(requestId.toString()))
                .andExpect(jsonPath("$.session_id").value(sessionId.toString()))
                .andExpect(jsonPath("$.request_status").value("RECEIVED"))
                .andExpect(jsonPath("$.generation_started").value(false))
                .andExpect(jsonPath("$.occasion").value("work"))
                .andExpect(jsonPath("$.style_context").value("minimal"))
                .andExpect(jsonPath("$.max_budget_vnd").value(850000))
                .andExpect(jsonPath("$.constraints[0].constraint_kind").value("EXCLUDE_COLOR"))
                .andExpect(jsonPath("$.constraints[0].text_value").value("RED"));
    }

    @Test
    void embeddedServerAppliesSameBodyPolicyToCanonicalAndMvcEncodedRoutes() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID sessionId = createSession(owner, List.of("CUSTOMER"));
        UUID requestId = createRequest(owner, sessionId, "{\"occasion\":\"work\"}");
        String auth = bearer(token(owner, List.of("CUSTOMER")));
        List<String> paths = List.of(
                "/api/v1/stylist/requests/" + requestId,
                "/api/v1/stylist/%72equests/" + requestId,
                "/api/v1/%73tylist/requests/" + requestId);
        long messagesBefore = messageRepository.count();
        long requestsBefore = stylingRequestRepository.count();
        long constraintsBefore = stylingConstraintRepository.count();
        org.mockito.Mockito.clearInvocations(stylingRequestService);

        for (String path : paths) {
            HttpResponse<String> rejected = sendHttp("GET", path, auth, "{}");
            assertThat(rejected.statusCode()).as(path).isEqualTo(400);
            assertThat(rejected.headers().firstValue("Cache-Control").orElse("")).isEqualTo("no-store");
            assertThat(objectMapper.readTree(rejected.body()).path("code").asText()).isEqualTo("INVALID_REQUEST");
        }
        org.mockito.Mockito.verify(stylingRequestService, org.mockito.Mockito.never())
                .readRequest(owner, requestId);

        for (String path : paths) {
            HttpResponse<String> accepted = sendHttp("GET", path, auth, null);
            assertThat(accepted.statusCode()).as(path).isEqualTo(200);
            assertThat(accepted.headers().firstValue("Cache-Control").orElse("")).isEqualTo("no-store");
            JsonNode response = objectMapper.readTree(accepted.body());
            assertThat(response.path("request_status").asText()).isEqualTo("RECEIVED");
            assertThat(response.path("generation_started").asBoolean()).isFalse();
        }
        org.mockito.Mockito.verify(stylingRequestService, org.mockito.Mockito.times(paths.size()))
                .readRequest(owner, requestId);
        assertThat(messageRepository.count()).isEqualTo(messagesBefore);
        assertThat(stylingRequestRepository.count()).isEqualTo(requestsBefore);
        assertThat(stylingConstraintRepository.count()).isEqualTo(constraintsBefore);
    }

    @Test
    void embeddedServerRejectsMissingInvalidExpiredAndUnsupportedJwtBeforeHandlers() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID sessionId = createSession(owner, List.of("CUSTOMER"));
        UUID requestId = createRequest(owner, sessionId, "{\"occasion\":\"work\"}");
        String requestPath = "/api/v1/stylist/requests/" + requestId;
        String intakePath = API + "/" + sessionId + "/requests";
        long messagesBefore = messageRepository.count();
        long requestsBefore = stylingRequestRepository.count();
        long constraintsBefore = stylingConstraintRepository.count();
        org.mockito.Mockito.clearInvocations(stylingRequestService);

        List<String> deniedTokens = Arrays.asList(null, "Bearer invalid-token",
                bearer(token(owner, List.of("CUSTOMER"), ISSUER, AUDIENCE,
                        Instant.now().minusSeconds(300), SIGNING_KEY, SignatureAlgorithm.RS256)));
        for (String auth : deniedTokens) {
            HttpResponse<String> read = sendHttp("GET", requestPath, auth, null);
            assertThat(read.statusCode()).isEqualTo(401);
            assertThat(read.headers().firstValue("Cache-Control").orElse("")).isEqualTo("no-store");
            HttpResponse<String> submit = sendHttp("POST", intakePath, auth,
                    "{\"message\":\"must not be stored\"}");
            assertThat(submit.statusCode()).isEqualTo(401);
            assertThat(submit.headers().firstValue("Cache-Control").orElse("")).isEqualTo("no-store");
        }
        for (String path : List.of(requestPath, intakePath)) {
            HttpResponse<String> unsupported = sendHttp(path.equals(requestPath) ? "GET" : "POST", path,
                    bearer(token(owner, List.of("SERVICE"))),
                    path.equals(requestPath) ? null : "{\"occasion\":\"work\"}");
            assertThat(unsupported.statusCode()).isEqualTo(403);
            assertThat(unsupported.headers().firstValue("Cache-Control").orElse("")).isEqualTo("no-store");
        }

        org.mockito.Mockito.verify(stylingRequestService, org.mockito.Mockito.never())
                .readRequest(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(stylingRequestService, org.mockito.Mockito.never())
                .createRequest(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
        assertThat(messageRepository.count()).isEqualTo(messagesBefore);
        assertThat(stylingRequestRepository.count()).isEqualTo(requestsBefore);
        assertThat(stylingConstraintRepository.count()).isEqualTo(constraintsBefore);
    }

    @Test
    void embeddedServerKeepsCustomerAndAdminRequestReadsSelfOnly() throws Exception {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        UUID sessionA = createSession(ownerA, List.of("CUSTOMER"));
        UUID requestA = createRequest(ownerA, sessionA, "{\"occasion\":\"work\"}");
        String path = "/api/v1/stylist/requests/" + requestA;

        assertThat(sendHttp("GET", path, bearer(token(ownerA, List.of("CUSTOMER"))), null).statusCode())
                .isEqualTo(200);
        assertThat(sendHttp("GET", path, bearer(token(ownerB, List.of("CUSTOMER"))), null).statusCode())
                .isEqualTo(404);
        assertThat(sendHttp("GET", path, bearer(token(ownerA, List.of("ADMIN"))), null).statusCode())
                .isEqualTo(200);
        HttpResponse<String> foreignAdmin = sendHttp("GET", path,
                bearer(token(ownerB, List.of("ADMIN"))), null);
        assertThat(foreignAdmin.statusCode()).isEqualTo(404);
        assertThat(foreignAdmin.headers().firstValue("Cache-Control").orElse("")).isEqualTo("no-store");
    }

    @Test
    void embeddedHttpRejectsNulWeatherBeforeAnyPostgresWrites() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID sessionId = createSession(owner, List.of("CUSTOMER"));
        long messagesBefore = messageRepository.count();
        long requestsBefore = stylingRequestRepository.count();
        long constraintsBefore = stylingConstraintRepository.count();
        org.mockito.Mockito.clearInvocations(stylingRequestService);

        HttpResponse<String> response = sendHttp("POST", API + "/" + sessionId + "/requests",
                bearer(token(owner, List.of("CUSTOMER"))),
                "{\"message\":\"need an outfit\",\"occasion\":\"work\","
                        + "\"general_weather_context\":\"hot\\u0000today\",\"constraints\":["
                        + "{\"constraint_kind\":\"EXCLUDE_COLOR\",\"text_value\":\"RED\",\"is_hard\":true}]}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).isEqualTo("no-store");
        assertThat(objectMapper.readTree(response.body()).path("code").asText()).isEqualTo("INVALID_REQUEST");
        org.mockito.Mockito.verify(stylingRequestService, org.mockito.Mockito.never())
                .createRequest(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
        assertThat(messageRepository.count()).isEqualTo(messagesBefore);
        assertThat(stylingRequestRepository.count()).isEqualTo(requestsBefore);
        assertThat(stylingConstraintRepository.count()).isEqualTo(constraintsBefore);
    }

    @Test
    void requestAndSessionOwnershipAreSelfOnlyForCustomerAndAdmin() throws Exception {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        UUID sessionA = createSession(ownerA, List.of("CUSTOMER"));
        UUID requestA = createRequest(ownerA, sessionA,
                "{\"occasion\":\"work\",\"constraints\":[]}");
        String tokenB = bearer(token(ownerB, List.of("CUSTOMER")));

        mockMvc.perform(post(API + "/" + sessionA + "/requests")
                        .contentType("application/json").content("{\"occasion\":\"work\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/stylist/requests/" + requestA))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(API + "/" + sessionA + "/requests")
                        .header("Authorization", bearer(token(UUID.randomUUID(), List.of("SERVICE"))))
                        .contentType("application/json").content("{\"occasion\":\"work\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/stylist/requests/" + requestA)
                        .header("Authorization", bearer(token(UUID.randomUUID(), List.of("SERVICE")))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(API + "/" + sessionA + "/requests")
                        .header("Authorization", tokenB).contentType("application/json")
                        .content("{\"occasion\":\"work\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        MvcResult foreign = mockMvc.perform(get("/api/v1/stylist/requests/" + requestA)
                        .header("Authorization", tokenB))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        MvcResult missing = mockMvc.perform(get("/api/v1/stylist/requests/" + UUID.randomUUID())
                        .header("Authorization", tokenB))
                .andExpect(status().isNotFound()).andReturn();
        JsonNode foreignBody = objectMapper.readTree(foreign.getResponse().getContentAsByteArray());
        JsonNode missingBody = objectMapper.readTree(missing.getResponse().getContentAsByteArray());
        assertThat(foreignBody.path("code").asText()).isEqualTo(missingBody.path("code").asText());
        assertThat(stylingRequestRepository.count()).isEqualTo(1);

        UUID adminA = UUID.randomUUID();
        UUID sessionAdmin = createSession(adminA, List.of("ADMIN"));
        UUID adminRequest = createRequest(adminA, sessionAdmin, List.of("ADMIN"), "{\"occasion\":\"admin test\"}");
        mockMvc.perform(get("/api/v1/stylist/requests/" + adminRequest)
                        .header("Authorization", bearer(token(adminA, List.of("ADMIN")))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/stylist/requests/" + adminRequest)
                        .header("Authorization", bearer(token(ownerB, List.of("ADMIN")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void referencedMessageMustBeUserMessageInTheSameSession() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID sessionId = createSession(owner, List.of("CUSTOMER"));
        UUID foreignSession = createSession(owner, List.of("CUSTOMER"));
        Instant now = Instant.now();
        StylistMessageEntity userMessage = messageRepository.save(new StylistMessageEntity(
                UUID.randomUUID(), sessionId, "USER", "please style me", now));
        StylistMessageEntity assistantMessage = messageRepository.save(new StylistMessageEntity(
                UUID.randomUUID(), sessionId, "ASSISTANT", "assistant text", now.plusMillis(1)));
        StylistMessageEntity foreignMessage = messageRepository.save(new StylistMessageEntity(
                UUID.randomUUID(), foreignSession, "USER", "foreign session", now.plusMillis(2)));
        String auth = bearer(token(owner, List.of("CUSTOMER")));

        mockMvc.perform(post(API + "/" + sessionId + "/requests").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"source_message_id\":\"" + userMessage.getStylistMessageId()
                                + "\",\"style_context\":\"minimal\"}"))
                .andExpect(status().isCreated());
        for (UUID invalidSource : List.of(assistantMessage.getStylistMessageId(),
                foreignMessage.getStylistMessageId(), UUID.randomUUID())) {
            mockMvc.perform(post(API + "/" + sessionId + "/requests").header("Authorization", auth)
                            .contentType("application/json")
                            .content("{\"source_message_id\":\"" + invalidSource
                                    + "\",\"style_context\":\"minimal\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }
        assertThat(stylingRequestRepository.count()).isEqualTo(1);
    }

    @Test
    void closedSessionReturnsConflictAndDoesNotAppendMessageOrRequest() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID sessionId = createSession(owner, List.of("CUSTOMER"));
        jdbcTemplate.update("UPDATE public.stylist_session SET closed_at = opened_at + INTERVAL '1 second' "
                + "WHERE stylist_session_id = ?", sessionId);

        mockMvc.perform(post(API + "/" + sessionId + "/requests")
                        .header("Authorization", bearer(token(owner, List.of("CUSTOMER"))))
                        .contentType("application/json")
                        .content("{\"message\":\"need an outfit\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_CLOSED"))
                .andExpect(header().string("Cache-Control", "no-store"));
        assertThat(stylingRequestRepository.count()).isZero();
        assertThat(stylingConstraintRepository.count()).isZero();
        assertThat(messageRepository.count()).isZero();
    }

    @Test
    void twoConcurrentValidIntakesOnOneSessionBothCreateIndependentRequests() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID sessionId = createSession(owner, List.of("CUSTOMER"));
        String auth = bearer(token(owner, List.of("CUSTOMER")));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        long requestsBefore = stylingRequestRepository.count();
        try {
            Future<HttpResponse<String>> first = executor.submit(() -> concurrentIntake(
                    ready, start, auth, API + "/" + sessionId + "/requests", "work"));
            Future<HttpResponse<String>> second = executor.submit(() -> concurrentIntake(
                    ready, start, auth, API + "/" + sessionId + "/requests", "weekend"));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            HttpResponse<String> firstResponse = first.get(30, TimeUnit.SECONDS);
            HttpResponse<String> secondResponse = second.get(30, TimeUnit.SECONDS);
            assertThat(firstResponse.statusCode()).isEqualTo(201);
            assertThat(secondResponse.statusCode()).isEqualTo(201);
            UUID firstId = UUID.fromString(objectMapper.readTree(firstResponse.body()).path("request_id").asText());
            UUID secondId = UUID.fromString(objectMapper.readTree(secondResponse.body()).path("request_id").asText());
            assertThat(firstId).isNotEqualTo(secondId);
            assertThat(stylingRequestRepository.count()).isEqualTo(requestsBefore + 2);
            assertThat(stylingRequestRepository.findById(firstId)).isPresent();
            assertThat(stylingRequestRepository.findById(secondId)).isPresent();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void intakeThatLocksOpenSessionFirstCompletesBeforeFixtureCloseCanCommit() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID sessionId = createSession(owner, List.of("CUSTOMER"));
        String auth = bearer(token(owner, List.of("CUSTOMER")));
        installSessionMessageGateTrigger();
        CountDownLatch gateLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseGateLock = new CountDownLatch(1);
        CountDownLatch closeAboutToUpdate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);

        try {
            Future<?> gateHolder = executor.submit(() -> {
                TransactionTemplate fixtureTransaction = new TransactionTemplate(transactionManager);
                fixtureTransaction.executeWithoutResult(status -> {
                    jdbcTemplate.execute("SELECT pg_advisory_xact_lock(17007, 17003)");
                    gateLockAcquired.countDown();
                    try {
                        if (!releaseGateLock.await(20, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out waiting to release test advisory lock");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while holding test advisory lock", exception);
                    }
                });
            });
            assertThat(gateLockAcquired.await(10, TimeUnit.SECONDS)).isTrue();

            Future<HttpResponse<String>> intake = executor.submit(() -> sendHttp("POST",
                    API + "/" + sessionId + "/requests", auth,
                    "{\"message\":\"one serialized intake\",\"constraints\":["
                            + "{\"constraint_kind\":\"EXCLUDE_COLOR\",\"text_value\":\"RED\",\"is_hard\":true}]}"));
            assertThat(awaitMessageInsertLockWait(10, TimeUnit.SECONDS)).isTrue();

            Future<?> close = executor.submit(() -> {
                TransactionTemplate fixtureTransaction = new TransactionTemplate(transactionManager);
                fixtureTransaction.executeWithoutResult(status -> {
                    jdbcTemplate.execute("SET LOCAL application_name = 'p03b1-close-lock-wait'");
                    closeAboutToUpdate.countDown();
                    jdbcTemplate.update("UPDATE public.stylist_session "
                            + "SET closed_at = opened_at + INTERVAL '1 second' "
                            + "WHERE stylist_session_id = ?", sessionId);
                });
            });
            assertThat(closeAboutToUpdate.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(awaitDatabaseLockWait("p03b1-close-lock-wait", 10, TimeUnit.SECONDS)).isTrue();

            releaseGateLock.countDown();
            gateHolder.get(20, TimeUnit.SECONDS);
            HttpResponse<String> intakeResponse = intake.get(20, TimeUnit.SECONDS);
            close.get(20, TimeUnit.SECONDS);
            assertThat(intakeResponse.statusCode()).isEqualTo(201);
            assertThat(stylingRequestRepository.count()).isEqualTo(1);
            assertThat(stylingConstraintRepository.count()).isEqualTo(1);
            assertThat(messageRepository.count()).isEqualTo(1);
            assertThat(sessionRepository.findById(sessionId).orElseThrow().getClosedAt()).isNotNull();
        } finally {
            releaseGateLock.countDown();
            executor.shutdownNow();
            removeSessionMessageGateTrigger();
        }
    }

    @Test
    void invalidRequestsAreRejectedBeforeAnyDatabaseWrite() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID sessionId = createSession(owner, List.of("CUSTOMER"));
        String auth = bearer(token(owner, List.of("CUSTOMER")));
        for (String body : List.of(
                "{}", "{\"unknown\":true,\"occasion\":\"work\"}",
                "{\"occasion\":\"work\",\"occasion\":\"party\"}",
                "{\"occasion\":\"work\"} {}", "[]", "null", "{broken}",
                "{\"message\":\"a\",\"source_message_id\":\"00000000-0000-4000-8000-000000000001\"}",
                "{\"occasion\":\"work\",\"budget_is_hard\":true}",
                "{\"occasion\":\"work\",\"max_budget_vnd\":1.5}",
                "{\"occasion\":\"work\",\"max_budget_vnd\":1e2}",
                "{\"occasion\":\"work\",\"budget_is_hard\":null}",
                "{\"source_message_id\":\"invalid\",\"occasion\":\"work\"}",
                "{\"occasion\":\"work\",\"constraints\":[{\"constraint_kind\":\"EXCLUDE_UNKNOWN\",\"catalog_entity_id\":\"00000000-0000-4000-8000-000000000001\",\"is_hard\":true}]}",
                "{\"occasion\":\"work\",\"constraints\":null}",
                "{\"occasion\":\"work\",\"constraints\":[{\"constraint_kind\":\"EXCLUDE_COLOR\",\"text_value\":\"RED\",\"is_hard\":true},{\"constraint_kind\":\"EXCLUDE_COLOR\",\"text_value\":\"red\",\"is_hard\":false}]}")) {
            mockMvc.perform(post(API + "/" + sessionId + "/requests").header("Authorization", auth)
                            .contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        MvcResult noContext = mockMvc.perform(post(API + "/" + sessionId + "/requests")
                        .header("Authorization", auth).contentType("application/json")
                        .content("{\"constraints\":[{\"constraint_kind\":\"EXCLUDE_COLOR\",\"text_value\":\"RED\",\"is_hard\":true}]}"))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(objectMapper.readTree(noContext.getResponse().getContentAsByteArray())
                .path("code").asText()).isEqualTo("INVALID_CONTEXT");
        assertThat(stylingRequestRepository.count()).isZero();
        assertThat(messageRepository.count()).isZero();

        String repeatedConstraint = "{\"constraint_kind\":\"EXCLUDE_COLOR\",\"text_value\":\"RED\",\"is_hard\":true}";
        String tooMany = "{\"message\":\"need an outfit\",\"constraints\":["
                + (repeatedConstraint + ",").repeat(20) + repeatedConstraint + "]}";
        mockMvc.perform(post(API + "/" + sessionId + "/requests").header("Authorization", auth)
                        .contentType("application/json").content(tooMany))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/stylist/requests/" + UUID.randomUUID())
                        .header("Authorization", auth).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(stylingRequestRepository.count()).isZero();
    }

    @Test
    void requestAndConstraintWritesRollBackTogether() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID sessionId = createSession(owner, List.of("CUSTOMER"));
        CreateStylingRequestRequest request = stylingRequestParser.parse("""
                {"message":"need a work outfit","constraints":[
                  {"constraint_kind":"EXCLUDE_COLOR","text_value":"RED","is_hard":true}]}
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            stylingRequestService.createRequest(owner, sessionId, request);
            throw new IllegalStateException("test rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(stylingRequestRepository.count()).isZero();
        assertThat(stylingConstraintRepository.count()).isZero();
        assertThat(messageRepository.count()).isZero();
    }

    @Test
    void serviceTransactionRollsBackMessageWhenRequestPersistenceFails() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID sessionId = createSession(owner, List.of("CUSTOMER"));
        AtomicBoolean messageWasVisibleInsideServiceTransaction = new AtomicBoolean();
        org.mockito.Mockito.doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            messageWasVisibleInsideServiceTransaction.set(messageRepository.count() == 1);
            throw new DataIntegrityViolationException("P03B1_TEST_AFTER_MESSAGE");
        }).when(stylingRequestRepository).saveAndFlush(org.mockito.ArgumentMatchers.any(StylingRequestEntity.class));

        MvcResult response = mockMvc.perform(post(API + "/" + sessionId + "/requests")
                        .header("Authorization", bearer(token(owner, List.of("CUSTOMER"))))
                        .contentType("application/json")
                        .content("{\"message\":\"failure after message\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        assertThat(objectMapper.readTree(response.getResponse().getContentAsByteArray())
                .path("code").asText()).isEqualTo("INTERNAL_ERROR");
        assertThat(messageWasVisibleInsideServiceTransaction).isTrue();
        org.mockito.Mockito.verify(stylingRequestRepository)
                .saveAndFlush(org.mockito.ArgumentMatchers.any(StylingRequestEntity.class));
        assertNoIntakeRowsRemain();
    }

    @Test
    void serviceTransactionRollsBackMessageAndRequestWhenConstraintPersistenceFails() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID sessionId = createSession(owner, List.of("CUSTOMER"));
        AtomicBoolean priorWritesWereVisibleInsideServiceTransaction = new AtomicBoolean();
        org.mockito.Mockito.doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            Iterable<?> constraints = invocation.getArgument(0);
            priorWritesWereVisibleInsideServiceTransaction.set(messageRepository.count() == 1
                    && stylingRequestRepository.count() == 1);
            assertThat(constraints).hasSize(1);
            throw new DataIntegrityViolationException("P03B1_TEST_BEFORE_CONSTRAINTS_COMPLETE");
        }).when(stylingConstraintRepository).saveAll(org.mockito.ArgumentMatchers.any(Iterable.class));

        MvcResult response = mockMvc.perform(post(API + "/" + sessionId + "/requests")
                        .header("Authorization", bearer(token(owner, List.of("CUSTOMER"))))
                        .contentType("application/json")
                        .content("{\"message\":\"failure before constraints complete\",\"constraints\":["
                                + "{\"constraint_kind\":\"EXCLUDE_COLOR\",\"text_value\":\"RED\",\"is_hard\":true}]}"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        assertThat(objectMapper.readTree(response.getResponse().getContentAsByteArray())
                .path("code").asText()).isEqualTo("INTERNAL_ERROR");
        assertThat(priorWritesWereVisibleInsideServiceTransaction).isTrue();
        org.mockito.Mockito.verify(stylingConstraintRepository)
                .saveAll(org.mockito.ArgumentMatchers.any(Iterable.class));
        assertNoIntakeRowsRemain();
    }

    @Test
    void databaseConstraintFailureAtFlushRollsBackEveryIntakeWrite() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID sessionId = createSession(owner, List.of("CUSTOMER"));
        installConstraintFlushFailureTrigger();
        try {
            MvcResult response = mockMvc.perform(post(API + "/" + sessionId + "/requests")
                            .header("Authorization", bearer(token(owner, List.of("CUSTOMER"))))
                            .contentType("application/json")
                            .content("{\"message\":\"database flush failure\",\"constraints\":["
                                    + "{\"constraint_kind\":\"EXCLUDE_COLOR\",\"text_value\":\"RED\",\"is_hard\":true}]}"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andReturn();

            assertThat(objectMapper.readTree(response.getResponse().getContentAsByteArray())
                    .path("code").asText()).isEqualTo("INTERNAL_ERROR");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT is_called FROM public.p03b1_constraint_failure_seq", Boolean.class)).isTrue();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT last_value FROM public.p03b1_constraint_failure_seq", Long.class)).isEqualTo(1L);
            assertNoIntakeRowsRemain();
        } finally {
            removeConstraintFlushFailureTrigger();
        }
    }

    @Test
    void requestBodyLimitAndUnsupportedMediaTypeAreEnforced() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID sessionId = createSession(owner, List.of("CUSTOMER"));
        String auth = bearer(token(owner, List.of("CUSTOMER")));
        mockMvc.perform(post(API + "/" + sessionId + "/requests").header("Authorization", auth)
                        .contentType("text/plain").content("{}"))
                .andExpect(status().isUnsupportedMediaType());
        String prefix = "{\"message\":\"need an outfit\",\"style_context\":\"";
        int padding = SessionRequestPolicyFilter.MAX_BODY_BYTES - prefix.getBytes(
                java.nio.charset.StandardCharsets.UTF_8).length - 2;
        String oversized = prefix + "x".repeat(padding + 1) + "\"}";
        mockMvc.perform(post(API + "/" + sessionId + "/requests").header("Authorization", auth)
                        .contentType("application/json").content(oversized))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
                .andExpect(header().string("Cache-Control", "no-store"));
        assertThat(stylingRequestRepository.count()).isZero();
    }

    @Test
    void migrationConstraintsAndServiceTransactionRollbackAreEnforced() {
        Instant now = Instant.parse("2026-10-29T12:30:00Z");
        UUID sessionId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        sessionRepository.save(new StylistSessionEntity(sessionId, owner, null, now, null));
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO public.stylist_session
                    (stylist_session_id, customer_id, opened_at, closed_at)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), owner, now, now.minusSeconds(1)))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO public.stylist_message
                    (stylist_message_id, stylist_session_id, speaker_role, content, sent_at)
                VALUES (?, ?, 'SYSTEM', 'x', ?)
                """, UUID.randomUUID(), sessionId, now))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO public.stylist_message
                    (stylist_message_id, stylist_session_id, speaker_role, content, sent_at)
                VALUES (?, ?, 'USER', 'x', ?)
                """, UUID.randomUUID(), UUID.randomUUID(), now))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO public.styling_request
                    (styling_request_id, stylist_session_id, max_budget_vnd, budget_is_hard, request_status, requested_at)
                VALUES (?, ?, NULL, TRUE, 'RECEIVED', CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), sessionId))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO public.styling_request
                    (styling_request_id, stylist_session_id, max_budget_vnd, budget_is_hard, request_status, requested_at)
                VALUES (?, ?, -1, FALSE, 'RECEIVED', CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), sessionId))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        for (String lifecycleStatus : List.of("PROCESSING", "SUCCEEDED", "NO_RESULT", "FAILED")) {
            jdbcTemplate.update("""
                    INSERT INTO public.styling_request
                        (styling_request_id, stylist_session_id, budget_is_hard, request_status, requested_at)
                    VALUES (?, ?, FALSE, ?, CURRENT_TIMESTAMP)
                    """, UUID.randomUUID(), sessionId, lifecycleStatus);
        }
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO public.styling_request
                    (styling_request_id, stylist_session_id, budget_is_hard, request_status, requested_at)
                VALUES (?, ?, FALSE, 'UNKNOWN', CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), sessionId))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);

        UUID requestId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO public.styling_request
                    (styling_request_id, stylist_session_id, max_budget_vnd, budget_is_hard, request_status, requested_at)
                VALUES (?, ?, 500000, FALSE, 'RECEIVED', CURRENT_TIMESTAMP)
                """, requestId, sessionId);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO public.styling_constraint
                    (constraint_id, styling_request_id, constraint_kind, catalog_entity_id, is_hard)
                VALUES (?, ?, 'UNKNOWN', ?, TRUE)
                """, UUID.randomUUID(), requestId, UUID.randomUUID()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO public.styling_constraint
                    (constraint_id, styling_request_id, constraint_kind, catalog_entity_id, is_hard)
                VALUES (?, ?, 'EXCLUDE_COLOR', ?, TRUE)
                """, UUID.randomUUID(), requestId, UUID.randomUUID()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        UUID rolledBack = UUID.randomUUID();
        transaction.executeWithoutResult(status -> {
            sessionRepository.save(new StylistSessionEntity(rolledBack, owner, "rollback", now, null));
            status.setRollbackOnly();
        });
        assertThat(sessionRepository.findByStylistSessionIdAndCustomerId(rolledBack, owner)).isEmpty();
        List<String> checks = jdbcTemplate.queryForList("""
                SELECT conname FROM pg_constraint
                WHERE conname IN ('ck_stylist_session_closed_after_open',
                                  'ck_stylist_message_speaker_role',
                                  'uq_stylist_message_session_message',
                                  'ck_styling_request_budget_nonnegative',
                                  'ck_styling_request_hard_budget_has_amount',
                                  'ck_styling_request_status',
                                  'ck_styling_constraint_kind',
                                  'ck_styling_constraint_one_value',
                                  'ck_styling_constraint_value_matches_kind')
                """, String.class);
        assertThat(checks).containsExactlyInAnyOrder("ck_stylist_session_closed_after_open",
                "ck_stylist_message_speaker_role", "uq_stylist_message_session_message",
                "ck_styling_request_budget_nonnegative", "ck_styling_request_hard_budget_has_amount",
                "ck_styling_request_status", "ck_styling_constraint_kind",
                "ck_styling_constraint_one_value", "ck_styling_constraint_value_matches_kind");
    }

    private HttpResponse<String> concurrentIntake(CountDownLatch ready, CountDownLatch start, String authorization,
                                                 String path, String occasion) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Intake start gate timed out");
        return sendHttp("POST", path, authorization, "{\"occasion\":\"" + occasion + "\"}");
    }

    private HttpResponse<String> sendHttp(String method, String path, String authorization, String body)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + localServerPort + path))
                .header("Accept", "application/json");
        if (authorization != null) request.header("Authorization", authorization);
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body, java.nio.charset.StandardCharsets.UTF_8));
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private boolean awaitDatabaseLockWait(String applicationName, long timeout, TimeUnit unit) {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        do {
            Boolean blocked = jdbcTemplate.queryForObject("""
                    SELECT EXISTS (
                        SELECT 1 FROM pg_stat_activity
                        WHERE application_name = ?
                          AND wait_event_type = 'Lock'
                          AND cardinality(pg_blocking_pids(pid)) > 0
                    )
                    """, Boolean.class, applicationName);
            if (Boolean.TRUE.equals(blocked)) return true;
            Thread.yield();
        } while (System.nanoTime() < deadline);
        return false;
    }

    private boolean awaitMessageInsertLockWait(long timeout, TimeUnit unit) {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        do {
            Boolean blocked = jdbcTemplate.queryForObject("""
                    SELECT EXISTS (
                        SELECT 1 FROM pg_stat_activity
                        WHERE wait_event_type = 'Lock'
                          AND query ILIKE '%insert%stylist_message%'
                    )
                    """, Boolean.class);
            if (Boolean.TRUE.equals(blocked)) return true;
            Thread.yield();
        } while (System.nanoTime() < deadline);
        return false;
    }

    private void assertNoIntakeRowsRemain() {
        assertThat(messageRepository.count()).isZero();
        assertThat(stylingRequestRepository.count()).isZero();
        assertThat(stylingConstraintRepository.count()).isZero();
    }

    private void installConstraintFlushFailureTrigger() {
        removeConstraintFlushFailureTrigger();
        jdbcTemplate.execute("CREATE SEQUENCE public.p03b1_constraint_failure_seq START WITH 1");
        jdbcTemplate.execute("""
                CREATE FUNCTION public.p03b1_constraint_failure_fn() RETURNS trigger
                LANGUAGE plpgsql AS $p03b1$
                BEGIN
                    PERFORM nextval('public.p03b1_constraint_failure_seq');
                    RAISE EXCEPTION 'P03B1_TEST_CONSTRAINT_FLUSH_FAILURE';
                END;
                $p03b1$
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER p03b1_constraint_failure_trigger
                BEFORE INSERT ON public.styling_constraint
                FOR EACH ROW EXECUTE FUNCTION public.p03b1_constraint_failure_fn()
                """);
    }

    private void removeConstraintFlushFailureTrigger() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS p03b1_constraint_failure_trigger ON public.styling_constraint");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS public.p03b1_constraint_failure_fn()");
        jdbcTemplate.execute("DROP SEQUENCE IF EXISTS public.p03b1_constraint_failure_seq");
    }

    private void installSessionMessageGateTrigger() {
        removeSessionMessageGateTrigger();
        jdbcTemplate.execute("""
                CREATE FUNCTION public.p03b1_message_gate_fn() RETURNS trigger
                LANGUAGE plpgsql AS $p03b1$
                BEGIN
                    PERFORM pg_advisory_xact_lock(17007, 17003);
                    RETURN NEW;
                END;
                $p03b1$
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER p03b1_message_gate_trigger
                BEFORE INSERT ON public.stylist_message
                FOR EACH ROW EXECUTE FUNCTION public.p03b1_message_gate_fn()
                """);
    }

    private void removeSessionMessageGateTrigger() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS p03b1_message_gate_trigger ON public.stylist_message");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS public.p03b1_message_gate_fn()");
    }

    private UUID createSession(UUID owner, List<String> roles) throws Exception {
        MvcResult result = mockMvc.perform(post(API).header("Authorization", bearer(token(owner, roles)))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .path("session_id").asText());
    }

    private Claim concurrentClaim(UUID owner, UUID requestId, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("claim start gate timed out");
        return generationPersistence.claim(owner, requestId);
    }

    private static PersistedRecommendation persistedRecommendation(UUID recommendationId) {
        return new PersistedRecommendation(recommendationId, "A grounded outfit explanation", "Keep the layers light",
                Instant.now(), List.of(new PersistedItem(UUID.randomUUID(), "TOP", "The top matches the need", 1),
                new PersistedItem(UUID.randomUUID(), "BOTTOM", "The bottom completes the outfit", 2)));
    }

    private UUID createRequest(UUID owner, UUID sessionId, String body) throws Exception {
        return createRequest(owner, sessionId, List.of("CUSTOMER"), body);
    }

    private UUID createRequest(UUID owner, UUID sessionId, List<String> roles, String body) throws Exception {
        MvcResult result = mockMvc.perform(post(API + "/" + sessionId + "/requests")
                        .header("Authorization", bearer(token(owner, roles)))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .path("request_id").asText());
    }

    private static String bearer(String token) { return "Bearer " + token; }

    private static String token(UUID subject, List<String> roles) {
        return token(subject.toString(), roles, ISSUER, AUDIENCE, Instant.now().plusSeconds(300),
                SIGNING_KEY, SignatureAlgorithm.RS256);
    }

    private static String token(UUID subject, List<String> roles, String issuer, String audience,
                                Instant expiresAt, RSAKey key, SignatureAlgorithm algorithm) {
        return token(subject.toString(), roles, issuer, audience, expiresAt, key, algorithm);
    }

    private static String token(String subject, List<String> roles, String issuer, String audience,
                                Instant expiresAt, RSAKey key, SignatureAlgorithm algorithm) {
        Instant issuedAt = expiresAt.isAfter(Instant.now())
                ? Instant.now().minusSeconds(2)
                : expiresAt.minusSeconds(60);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(subject)
                .id(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", roles)
                .build();
        JwsHeader header = JwsHeader.with(algorithm).keyId(key.getKeyID()).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(key)))
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
    static class TestJwtConfiguration {
        @Bean
        @org.springframework.context.annotation.Primary
        JwtDecoder testJwtDecoder() throws Exception {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(SIGNING_KEY.toRSAPublicKey())
                    .signatureAlgorithm(SignatureAlgorithm.RS256).build();
            decoder.setJwtValidator(JwtContractValidator.forIdentity(ISSUER, AUDIENCE));
            return decoder;
        }
    }
}
