package com.stylemind.stylist.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doAnswer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.stylemind.stylist.StylistServiceApplication;
import com.stylemind.stylist.client.commerce.CommerceCatalogClient;
import com.stylemind.stylist.client.commerce.CommerceCatalogFeignClient;
import com.stylemind.stylist.dto.recommendation.InternalRecommendationResult;
import com.stylemind.stylist.entity.constraint.StylingConstraintEntity;
import com.stylemind.stylist.entity.message.StylistMessageEntity;
import com.stylemind.stylist.entity.request.StylingRequestEntity;
import com.stylemind.stylist.entity.session.StylistSessionEntity;
import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import com.stylemind.stylist.model.retrieval.RetrievalModel;
import com.stylemind.stylist.model.retrieval.RetrievalModel.CandidateSearchQuery;
import com.stylemind.stylist.model.retrieval.RetrievalModel.CandidateSearchResult;
import com.stylemind.stylist.model.retrieval.RetrievalModel.CatalogCandidate;
import com.stylemind.stylist.model.retrieval.RetrievalModel.IndexReport;
import com.stylemind.stylist.repository.constraint.StylingConstraintRepository;
import com.stylemind.stylist.repository.message.StylistMessageRepository;
import com.stylemind.stylist.repository.request.StylingRequestRepository;
import com.stylemind.stylist.repository.retrieval.QdrantHttpVectorStore;
import com.stylemind.stylist.repository.session.StylistSessionRepository;
import com.stylemind.stylist.service.impl.retrieval.CandidateRetrievalServiceImpl;
import com.stylemind.stylist.service.impl.recommendation.StylingRecommendationServiceImpl;
import com.stylemind.stylist.service.recommendation.StylingRecommendationService;
import com.stylemind.stylist.service.retrieval.CandidateRetrievalService;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cloud.openfeign.FeignClientBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** End-to-end internal wiring with only disposable PostgreSQL/Qdrant and a Commerce HTTP contract fixture. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = StylistServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.clean-disabled=true",
            "management.health.neo4j.enabled=false",
            "spring.neo4j.uri=bolt://127.0.0.1:1",
            "stylemind.security.jwt.jwk-set-uri=",
            "stylemind.security.jwt.jws-algorithm=RS256",
            "stylemind.security.jwt.issuer=https://identity.stylemind.local",
            "stylemind.security.jwt.audience=stylemind-api",
            "stylemind.stylist.knowledge.enabled=false",
            "stylemind.stylist.knowledge.neo4j-uri=bolt://127.0.0.1:1",
            "stylemind.stylist.knowledge.artifact-id=",
            "stylemind.stylist.knowledge.artifact-version=",
            "stylemind.openai.enabled=false",
            "stylemind.clients.commerce.connect-timeout-ms=300",
            "stylemind.clients.commerce.read-timeout-ms=400"
        })
@Import(InternalRecommendationPipelineIntegrationTests.DisposableRetrievalConfiguration.class)
class InternalRecommendationPipelineIntegrationTests {
    private static final int QDRANT_PORT = 6333;
    private static final CommerceHttpFixture COMMERCE = CommerceHttpFixture.start();
    private static TestCandidateRetrieval LIVE_RETRIEVAL;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("stylemind_stylist_pipeline_test")
            .withUsername("stylist_pipeline_test")
            .withPassword("stylist_pipeline_test_only");

    @Container
    static final GenericContainer<?> QDRANT = new GenericContainer<>(DockerImageName.parse("qdrant/qdrant:v1.20.0"))
            .withExposedPorts(QDRANT_PORT)
            .waitingFor(Wait.forHttp("/collections").forPort(QDRANT_PORT).forStatusCode(200)
                    .withStartupTimeout(Duration.ofSeconds(90)));

    @DynamicPropertySource
    static void disposableEndpoints(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("stylemind.clients.commerce.base-url", COMMERCE::baseUrl);
    }

    @Autowired StylingRecommendationService recommendationService;
    @Autowired StylistSessionRepository sessionRepository;
    @Autowired StylistMessageRepository messageRepository;
    @Autowired StylingRequestRepository requestRepository;
    @Autowired StylingConstraintRepository constraintRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ApplicationContext applicationContext;
    @Autowired ObjectProvider<TestCandidateRetrieval> retrievalProvider;
    @SpyBean CommerceCatalogClient commerceClient;

    @BeforeEach
    void clearDisposableStateAndAssertRemoteCallsAreOutsideTransactions() {
        constraintRepository.deleteAllInBatch();
        requestRepository.deleteAllInBatch();
        messageRepository.deleteAllInBatch();
        sessionRepository.deleteAllInBatch();
        COMMERCE.reset();
        TestCandidateRetrieval retrieval = retrievalProvider.getObject();
        retrieval.resetObservations();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return invocation.callRealMethod();
        }).when(commerceClient).hydrate(anyMap());
    }

    @AfterAll
    static void cleanOnlyResourcesOwnedByThisTest() {
        if (LIVE_RETRIEVAL != null) LIVE_RETRIEVAL.closeOwnedCollection();
        COMMERCE.close();
    }

    @Test
    void wiresOwnerContextThroughRealQdrantFeignAndP02WithoutWritingRecommendations() {
        UUID owner = UUID.randomUUID();
        UUID requestId = createRequest(owner, 448_000L, true, null, null);
        long messagesBefore = messageRepository.count();
        long requestsBefore = requestRepository.count();
        long constraintsBefore = constraintRepository.count();

        InternalRecommendationResult result = recommendationService.recommend(owner, requestId);

        assertThat(result.requestId()).isEqualTo(requestId);
        assertThat(result.candidatesUsed()).isEqualTo(6);
        assertThat(result.outfits()).isNotEmpty().hasSizeLessThanOrEqualTo(3)
                .allSatisfy(outfit -> {
                    assertThat(outfit.items()).hasSize(2);
                    assertThat(outfit.items()).extracting(InternalRecommendationResult.RecommendedItem::slot)
                            .containsExactlyInAnyOrder("TOP", "BOTTOM");
                    assertThat(outfit.totalPriceVnd()).isLessThanOrEqualTo(448_000L);
                    assertThat(outfit.items()).allSatisfy(item -> {
                        assertThat(item.productId()).isNotNull();
                        assertThat(item.variantId()).isNotNull();
                        assertThat(item.images()).hasSize(1);
                        assertThat(item.unitPriceVnd()).isPositive();
                    });
                });
        assertThat(result.outfits()).anySatisfy(outfit -> assertThat(outfit.totalPriceVnd()).isEqualTo(448_000L));
        List<UUID> permittedVariantIds = P04FixtureLoader.load().stream()
                .filter(candidate -> "APPROVED_TEST_ONLY".equals(candidate.fixtureEligibilityStatus())
                        && "WOMEN".equals(candidate.demographicSegmentRaw()) && candidate.reviewSignals().isEmpty())
                .map(CatalogCandidate::variantId).toList();
        assertThat(retrievalProvider.getObject().observedQueries()).extracting(CandidateSearchQuery::slot)
                .containsExactly("TOP", "BOTTOM");
        assertThat(result.outfits()).allSatisfy(outfit -> assertThat(outfit.items())
                .allSatisfy(item -> assertThat(item.variantId()).isIn(permittedVariantIds)));
        assertThat(COMMERCE.capturedRequests()).isNotEmpty().allSatisfy(request -> {
            assertThat(request.hasAuthorization()).isFalse();
            assertThat(request.hasServiceToken()).isFalse();
        });
        assertThat(COMMERCE.capturedRequests()).anySatisfy(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/api/v1/variants/batch-lookup");
            assertThat(request.variantIds()).hasSize(6);
        });
        assertThat(requestRepository.findById(requestId).orElseThrow().getRequestStatus()).isEqualTo("RECEIVED");
        assertThat(messageRepository.count()).isEqualTo(messagesBefore);
        assertThat(requestRepository.count()).isEqualTo(requestsBefore);
        assertThat(constraintRepository.count()).isEqualTo(constraintsBefore);
        assertThat(flywayVersion()).isEqualTo(3);
    }

    @Test
    void ownerARequestCannotBeReadForCustomerBOrAdminSubjectAndNoRemoteCallOccurs() {
        UUID ownerA = UUID.randomUUID();
        UUID requestId = createRequest(ownerA, null, false, null, null);
        long requests = COMMERCE.requestCount();
        long searches = retrievalProvider.getObject().searchCount();
        UUID ownerB = UUID.randomUUID();
        UUID adminSubject = UUID.randomUUID();

        assertCode(ErrorCode.RESOURCE_NOT_FOUND, () -> recommendationService.recommend(ownerB, requestId));
        assertCode(ErrorCode.RESOURCE_NOT_FOUND, () -> recommendationService.recommend(adminSubject, requestId));

        assertThat(COMMERCE.requestCount()).isEqualTo(requests);
        assertThat(retrievalProvider.getObject().searchCount()).isEqualTo(searches);
    }

    @Test
    void suspendsAnOuterTransactionBeforeAnyRetrievalOrCommerceCall() {
        UUID owner = UUID.randomUUID();
        UUID requestId = createRequest(owner, null, false, null, null);
        TransactionTemplate callerTransaction = new TransactionTemplate(transactionManager);

        callerTransaction.execute(status -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            InternalRecommendationResult result = recommendationService.recommend(owner, requestId);
            assertThat(result.outfits()).isNotEmpty();
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return null;
        });
    }

    @Test
    void hardBudgetIsInclusiveAndOneVndUnderReturnsNoOutfit() {
        UUID owner = UUID.randomUUID();
        UUID exactRequest = createRequest(owner, 448_000L, true, null, null);
        InternalRecommendationResult exact = recommendationService.recommend(owner, exactRequest);
        assertThat(exact.outfits()).isNotEmpty();
        assertThat(exact.outfits()).anySatisfy(outfit -> assertThat(outfit.totalPriceVnd()).isEqualTo(448_000L));

        clearRecordsOnly();
        COMMERCE.reset();
        UUID belowRequest = createRequest(owner, 447_999L, true, null, null);
        InternalRecommendationResult below = recommendationService.recommend(owner, belowRequest);
        assertThat(below.outfits()).isEmpty();
        assertThat(below.warnings()).contains("NO_RESULT_WITHIN_BOUNDED_SEARCH");
    }

    @Test
    void overflowIsReportedAsNoOutfitAndNotAsAFalseSuccessfulPrice() {
        UUID owner = UUID.randomUUID();
        UUID requestId = createRequest(owner, null, false, null, null);
        COMMERCE.afterBatch((call, ids) -> {
            if (call == 1) COMMERCE.setAllActivePrices(Long.MAX_VALUE);
        });

        InternalRecommendationResult result = recommendationService.recommend(owner, requestId);

        assertThat(result.outfits()).isEmpty();
        assertThat(result.warnings()).contains("OUTFIT_PRICE_OVERFLOW", "NO_RESULT_WITHIN_BOUNDED_SEARCH");
    }

    @Test
    void finalRecheckUsesChangedCurrentPriceAndRejectsAnIncreaseAboveHardBudget() {
        UUID owner = UUID.randomUUID();
        UUID cheaperRequest = createRequest(owner, 448_000L, true, null, null);
        COMMERCE.afterBatch((call, ids) -> {
            if (call == 2) COMMERCE.adjustPrices(ids, -1_000L);
        });
        InternalRecommendationResult cheaper = recommendationService.recommend(owner, cheaperRequest);
        assertThat(cheaper.outfits()).isNotEmpty().allSatisfy(outfit -> {
            assertThat(outfit.totalPriceVnd()).isLessThan(448_000L);
            assertThat(outfit.items().stream().mapToLong(InternalRecommendationResult.RecommendedItem::unitPriceVnd)
                    .sum()).isEqualTo(outfit.totalPriceVnd());
        });

        clearRecordsOnly();
        COMMERCE.reset();
        UUID higherRequest = createRequest(owner, 448_000L, true, null, null);
        COMMERCE.afterBatch((call, ids) -> {
            if (call == 2) COMMERCE.adjustPrices(ids, 1L);
        });
        InternalRecommendationResult higher = recommendationService.recommend(owner, higherRequest);
        assertThat(higher.outfits()).isEmpty();
    }

    @Test
    void finalRecheckRejectsChangedStatusCategoryDemographicAndHardExcludedColor() {
        UUID owner = UUID.randomUUID();

        UUID inactiveRequest = createRequest(owner, null, false, null, null);
        COMMERCE.afterBatch((call, ids) -> {
            if (call == 2) COMMERCE.setVariantStatus(ids, "INACTIVE");
        });
        assertThat(recommendationService.recommend(owner, inactiveRequest).outfits()).isEmpty();

        clearRecordsOnly();
        COMMERCE.reset();
        UUID categoryRequest = createRequest(owner, null, false, null, null);
        COMMERCE.afterBatch((call, ids) -> {
            if (call == 2) COMMERCE.setCategory(ids, UUID.randomUUID());
        });
        assertThat(recommendationService.recommend(owner, categoryRequest).outfits()).isEmpty();

        clearRecordsOnly();
        COMMERCE.reset();
        UUID demographicRequest = createRequest(owner, null, false, null, null);
        COMMERCE.afterBatch((call, ids) -> {
            if (call == 2) COMMERCE.setDemographic(ids, "UNISEX");
        });
        assertThat(recommendationService.recommend(owner, demographicRequest).outfits()).isEmpty();

        clearRecordsOnly();
        COMMERCE.reset();
        UUID productStatusRequest = createRequest(owner, null, false, null, null);
        COMMERCE.afterBatch((call, ids) -> {
            if (call == 2) COMMERCE.setProductStatus(ids, "INACTIVE");
        });
        assertThat(recommendationService.recommend(owner, productStatusRequest).outfits()).isEmpty();

        clearRecordsOnly();
        COMMERCE.reset();
        UUID brandStatusRequest = createRequest(owner, null, false, null, null);
        COMMERCE.afterBatch((call, ids) -> {
            if (call == 2) COMMERCE.setBrandActive(ids, false);
        });
        assertThat(recommendationService.recommend(owner, brandStatusRequest).outfits()).isEmpty();

        clearRecordsOnly();
        COMMERCE.reset();
        UUID colorRequest = createRequest(owner, null, false, "Hồng", "EXCLUDE_COLOR");
        COMMERCE.afterBatch((call, ids) -> {
            if (call == 2) COMMERCE.setColors(ids, "Hồng");
        });
        assertThat(recommendationService.recommend(owner, colorRequest).outfits()).isEmpty();
    }

    @Test
    void commerceDisappearanceOrFailureNeverReturnsAStaleOrPartiallyFreshOutfit() {
        UUID owner = UUID.randomUUID();
        UUID disappearedRequest = createRequest(owner, null, false, null, null);
        COMMERCE.afterBatch((call, ids) -> {
            if (call == 2) COMMERCE.setCategoryActive(ids, false);
        });
        assertThat(recommendationService.recommend(owner, disappearedRequest).outfits()).isEmpty();

        clearRecordsOnly();
        COMMERCE.reset();
        UUID failedRequest = createRequest(owner, null, false, null, null);
        COMMERCE.afterBatch((call, ids) -> {
            if (call == 2) COMMERCE.mode(CommerceMode.HTTP_503);
        });
        assertCode(ErrorCode.DEPENDENCY_UNAVAILABLE, () -> recommendationService.recommend(owner, failedRequest));
    }

    @Test
    void rejectsCandidatesOutsideApprovedFixtureMapBeforeCommerceAndKeepsStableDiversity() {
        UUID owner = UUID.randomUUID();
        UUID requestId = createRequest(owner, null, false, null, null);
        TestCandidateRetrieval retrieval = retrievalProvider.getObject();
        CatalogCandidate invalid = copy(P04FixtureLoader.load().getFirst(), UUID.randomUUID(), "TOP");
        retrieval.overrideNext("TOP", new CandidateSearchResult(
                List.of(new RetrievalModel.RetrievedCandidate(invalid, 1.0))));
        assertCode(ErrorCode.DEPENDENCY_PROTOCOL_ERROR, () -> recommendationService.recommend(owner, requestId));
        assertThat(COMMERCE.requestCount()).isZero();

        clearRecordsOnly();
        COMMERCE.reset();
        retrieval.resetObservations();
        UUID stableRequest = createRequest(owner, null, false, null, null);
        InternalRecommendationResult first = recommendationService.recommend(owner, stableRequest);
        retrieval.resetObservations();
        COMMERCE.reset();
        InternalRecommendationResult second = recommendationService.recommend(owner, stableRequest);
        assertThat(first.outfits()).isEqualTo(second.outfits());
        assertThat(first.outfits()).extracting(outfit -> outfit.items().stream()
                .map(item -> item.variantId().toString()).sorted().toList()).doesNotHaveDuplicates();
    }

    @Test
    void actualFeignHttpContractHandlesMissingDuplicateMismatchedMalformedTimeoutAndStatuses() throws Exception {
        List<CatalogCandidate> approved = P04FixtureLoader.load().stream()
                .filter(candidate -> "APPROVED_TEST_ONLY".equals(candidate.fixtureEligibilityStatus()))
                .toList();
        CatalogCandidate candidate = approved.getFirst();
        Map<UUID, UUID> oneExpected = Map.of(candidate.variantId(), candidate.productId());

        assertThat(commerceClient.hydrate(oneExpected)).containsKey(candidate.variantId());
        assertThat(commerceClient.hydrate(Map.of(UUID.randomUUID(), candidate.productId()))).isEmpty();

        COMMERCE.mode(CommerceMode.DUPLICATE);
        assertCode(ErrorCode.DEPENDENCY_PROTOCOL_ERROR, () -> commerceClient.hydrate(oneExpected));
        COMMERCE.reset();
        COMMERCE.mode(CommerceMode.UNREQUESTED);
        assertCode(ErrorCode.DEPENDENCY_PROTOCOL_ERROR, () -> commerceClient.hydrate(oneExpected));
        COMMERCE.reset();
        COMMERCE.mode(CommerceMode.WRONG_PRODUCT);
        assertCode(ErrorCode.DEPENDENCY_PROTOCOL_ERROR, () -> commerceClient.hydrate(oneExpected));
        COMMERCE.reset();
        COMMERCE.mode(CommerceMode.NON_INTEGER_PRICE);
        assertCode(ErrorCode.DEPENDENCY_PROTOCOL_ERROR, () -> commerceClient.hydrate(oneExpected));
        COMMERCE.reset();
        COMMERCE.mode(CommerceMode.MALFORMED_SHAPE);
        assertCode(ErrorCode.DEPENDENCY_PROTOCOL_ERROR, () -> commerceClient.hydrate(oneExpected));
        COMMERCE.reset();
        COMMERCE.mode(CommerceMode.MALFORMED_JSON);
        assertCode(ErrorCode.DEPENDENCY_PROTOCOL_ERROR, () -> commerceClient.hydrate(oneExpected));
        COMMERCE.reset();
        COMMERCE.mode(CommerceMode.DETAIL_PRICE_DRIFT);
        assertCode(ErrorCode.DEPENDENCY_PROTOCOL_ERROR, () -> commerceClient.hydrate(oneExpected));
        COMMERCE.reset();
        COMMERCE.mode(CommerceMode.HTTP_400);
        assertCode(ErrorCode.DEPENDENCY_PROTOCOL_ERROR, () -> commerceClient.hydrate(oneExpected));
        COMMERCE.reset();
        COMMERCE.mode(CommerceMode.HTTP_503);
        assertCode(ErrorCode.DEPENDENCY_UNAVAILABLE, () -> commerceClient.hydrate(oneExpected));
        COMMERCE.reset();
        COMMERCE.mode(CommerceMode.PRODUCT_404);
        assertThat(commerceClient.hydrate(oneExpected)).isEmpty();
        COMMERCE.reset();
        COMMERCE.mode(CommerceMode.PRODUCT_400);
        assertCode(ErrorCode.DEPENDENCY_PROTOCOL_ERROR, () -> commerceClient.hydrate(oneExpected));
        COMMERCE.reset();
        COMMERCE.mode(CommerceMode.PRODUCT_503);
        assertCode(ErrorCode.DEPENDENCY_UNAVAILABLE, () -> commerceClient.hydrate(oneExpected));
        COMMERCE.reset();
        COMMERCE.mode(CommerceMode.SLOW);
        assertCode(ErrorCode.DEPENDENCY_UNAVAILABLE, () -> commerceClient.hydrate(oneExpected));
    }

    @Test
    void connectionFailureAndRedirectDoNotSendAuthorizationOrFollowToAnotherHost() throws Exception {
        CatalogCandidate candidate = P04FixtureLoader.load().getFirst();
        Map<UUID, UUID> expected = Map.of(candidate.variantId(), candidate.productId());
        int refusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            refusedPort = socket.getLocalPort();
        }
        CommerceCatalogFeignClient unavailableClient = new FeignClientBuilder(applicationContext)
                .forType(CommerceCatalogFeignClient.class, "commerceConnectionFailureProbe")
                .url("http://127.0.0.1:" + refusedPort).build();
        assertCode(ErrorCode.DEPENDENCY_UNAVAILABLE,
                () -> new CommerceCatalogClient(unavailableClient).hydrate(expected));

        COMMERCE.reset();
        COMMERCE.mode(CommerceMode.REDIRECT);
        assertCode(ErrorCode.DEPENDENCY_PROTOCOL_ERROR, () -> commerceClient.hydrate(expected));
        assertThat(COMMERCE.redirectTargetRequestCount()).isZero();
        assertThat(COMMERCE.capturedRequests()).allSatisfy(request -> {
            assertThat(request.hasAuthorization()).isFalse();
            assertThat(request.hasServiceToken()).isFalse();
        });
    }

    private UUID createRequest(UUID owner, Long budget, boolean hardBudget, String exclusionText, String exclusionKind) {
        Instant now = Instant.now();
        UUID sessionId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        sessionRepository.save(new StylistSessionEntity(sessionId, owner, null, now, null));
        messageRepository.save(new StylistMessageEntity(messageId, sessionId, "USER", "Outfit for everyday wear", now));
        requestRepository.save(new StylingRequestEntity(requestId, sessionId, messageId,
                "everyday wear", null, "casual", null, budget, hardBudget, "RECEIVED", now));
        if (exclusionKind != null) {
            constraintRepository.save(new StylingConstraintEntity(UUID.randomUUID(), requestId, exclusionKind,
                    null, exclusionText, true));
        }
        return requestId;
    }

    private void clearRecordsOnly() {
        constraintRepository.deleteAllInBatch();
        requestRepository.deleteAllInBatch();
        messageRepository.deleteAllInBatch();
        sessionRepository.deleteAllInBatch();
    }

    private int flywayVersion() {
        return jdbcTemplate.queryForObject("select max(version)::int from public.flyway_schema_history where success",
                Integer.class);
    }

    private static CatalogCandidate copy(CatalogCandidate source, UUID categoryId, String slot) {
        return new CatalogCandidate(source.productId(), source.variantId(), categoryId,
                source.categorySlug(), slot, source.demographicSegmentRaw(), source.productName(),
                source.descriptionRaw(), source.colorRaw(), source.sizeRaw(), source.materialRaw(),
                source.seedPriceVndSnapshot(), source.categoryMappingStatus(), source.fixtureEligibilityStatus(),
                source.reviewSignals(), source.fixtureTag(), source.source(), source.sourceFile(), source.sourceSha256(),
                source.sourceRuntimeStatus(), source.priceSemantics(), source.productStatusSource(),
                source.variantStatusSource());
    }

    private static void assertCode(ErrorCode expected, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(StylistApiException.class)
                .satisfies(exception -> assertThat(((StylistApiException) exception).errorCode()).isEqualTo(expected));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DisposableRetrievalConfiguration {
        @Bean
        @Lazy
        StylingRecommendationService fixtureRecommendationService(
                StylingRequestRepository requestRepository,
                StylingConstraintRepository constraintRepository,
                StylistMessageRepository messageRepository,
                ObjectProvider<CandidateRetrievalService> retrievalProvider,
                CommerceCatalogClient commerce,
                PlatformTransactionManager transactionManager) {
            return new StylingRecommendationServiceImpl(requestRepository, constraintRepository,
                    messageRepository, retrievalProvider, commerce, transactionManager);
        }

        @Bean
        @Lazy
        @Primary
        TestCandidateRetrieval fixtureRetrieval(ObjectMapper objectMapper) {
            String collection = "stylemind_p041_test_" + UUID.randomUUID().toString().replace("-", "");
            URI qdrantUri = URI.create("http://" + QDRANT.getHost() + ":" + QDRANT.getMappedPort(QDRANT_PORT));
            QdrantHttpVectorStore vectorStore = new QdrantHttpVectorStore(qdrantUri, objectMapper, Duration.ofSeconds(5));
            vectorStore.createCollection(collection);
            CandidateRetrievalServiceImpl delegate = new CandidateRetrievalServiceImpl(
                    vectorStore, new FakeTestOnlyDeterministicEmbedding(), collection);
            IndexReport report = delegate.indexFixtures(P04FixtureLoader.load());
            if (report.indexedCount() != 6) {
                vectorStore.deleteTestCollection(collection);
                throw new IllegalStateException("The P04.1 fixture set no longer has six approved rows");
            }
            LIVE_RETRIEVAL = new TestCandidateRetrieval(delegate, vectorStore, collection, report);
            return LIVE_RETRIEVAL;
        }
    }

    static final class TestCandidateRetrieval implements CandidateRetrievalService {
        private final CandidateRetrievalService delegate;
        private final QdrantHttpVectorStore vectorStore;
        private final String collection;
        private final IndexReport indexReport;
        private final List<CandidateSearchQuery> queries = new CopyOnWriteArrayList<>();
        private final AtomicInteger searchCount = new AtomicInteger();
        private final Map<String, CandidateSearchResult> overrides = new ConcurrentHashMap<>();

        TestCandidateRetrieval(CandidateRetrievalService delegate, QdrantHttpVectorStore vectorStore,
                               String collection, IndexReport indexReport) {
            this.delegate = delegate;
            this.vectorStore = vectorStore;
            this.collection = collection;
            this.indexReport = indexReport;
        }

        @Override public IndexReport indexFixtures(List<CatalogCandidate> candidates) {
            throw new UnsupportedOperationException("The fixture index is created once for this test-owned collection");
        }

        @Override public CandidateSearchResult search(CandidateSearchQuery query) {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new AssertionError("Qdrant search must not run inside a Stylist database transaction");
            }
            searchCount.incrementAndGet();
            queries.add(query);
            CandidateSearchResult override = overrides.remove(query.slot());
            return override == null ? delegate.search(query) : override;
        }

        List<CandidateSearchQuery> observedQueries() { return List.copyOf(queries); }
        int searchCount() { return searchCount.get(); }
        IndexReport indexReport() { return indexReport; }
        void overrideNext(String slot, CandidateSearchResult result) { overrides.put(slot, result); }
        void resetObservations() { queries.clear(); searchCount.set(0); overrides.clear(); }
        void closeOwnedCollection() { vectorStore.deleteTestCollection(collection); }
    }

    private enum CommerceMode {
        NORMAL, HTTP_400, HTTP_503, SLOW, REDIRECT, MALFORMED_JSON, MALFORMED_SHAPE, DUPLICATE,
        UNREQUESTED, WRONG_PRODUCT, NON_INTEGER_PRICE, DETAIL_PRICE_DRIFT,
        PRODUCT_404, PRODUCT_400, PRODUCT_503
    }

    private record CapturedCommerceRequest(String method, String path, boolean hasAuthorization,
                                           boolean hasServiceToken, List<UUID> variantIds) { }

    private static final class CommerceHttpFixture {
        private final ObjectMapper mapper = new ObjectMapper();
        private final HttpServer server;
        private final HttpServer redirectTarget;
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private final ExecutorService targetExecutor = Executors.newCachedThreadPool();
        private final Map<UUID, ProductState> products = new LinkedHashMap<>();
        private final Map<UUID, VariantState> variants = new LinkedHashMap<>();
        private final List<CapturedCommerceRequest> captured = new CopyOnWriteArrayList<>();
        private final AtomicInteger batchCalls = new AtomicInteger();
        private final AtomicInteger redirectTargetCalls = new AtomicInteger();
        private volatile CommerceMode mode = CommerceMode.NORMAL;
        private volatile BiConsumer<Integer, List<UUID>> batchObserver = (call, ids) -> { };

        private CommerceHttpFixture(HttpServer server, HttpServer redirectTarget) {
            this.server = server;
            this.redirectTarget = redirectTarget;
            for (CatalogCandidate row : P04FixtureLoader.load()) {
                if (!"APPROVED_TEST_ONLY".equals(row.fixtureEligibilityStatus())
                        || !"WOMEN".equals(row.demographicSegmentRaw()) || !row.reviewSignals().isEmpty()) continue;
                ProductState product = new ProductState(row.productId(), row.categoryId(),
                        "TOP".equals(row.slot()) ? "Top" : "Bottom", UUID.nameUUIDFromBytes(
                        ("brand:" + row.productId()).getBytes(StandardCharsets.UTF_8)), "Test Brand",
                        row.productName(), row.descriptionRaw(), "WOMEN", "ACTIVE");
                products.put(product.productId, product);
                VariantState variant = new VariantState(row.variantId(), row.productId(),
                        "SKU-" + row.variantId(), row.sizeRaw(), row.colorRaw(), row.materialRaw(),
                        row.seedPriceVndSnapshot(), "ACTIVE");
                variants.put(variant.variantId, variant);
            }
        }

        static CommerceHttpFixture start() {
            try {
                HttpServer target = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                HttpServer source = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                CommerceHttpFixture fixture = new CommerceHttpFixture(source, target);
                target.createContext("/redirect-target", exchange -> {
                    fixture.redirectTargetCalls.incrementAndGet();
                    fixture.respond(exchange, 200, fixture.mapper.createObjectNode().put("leak", true));
                });
                target.setExecutor(fixture.targetExecutor);
                target.start();
                source.createContext("/api/v1/variants/batch-lookup", fixture::batchLookup);
                source.createContext("/api/v1/products/", fixture::productDetail);
                source.setExecutor(fixture.executor);
                source.start();
                return fixture;
            } catch (IOException exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }

        String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        int requestCount() { return captured.size(); }
        int redirectTargetRequestCount() { return redirectTargetCalls.get(); }
        List<CapturedCommerceRequest> capturedRequests() { return List.copyOf(captured); }
        void mode(CommerceMode mode) { this.mode = mode; }
        void afterBatch(BiConsumer<Integer, List<UUID>> observer) { this.batchObserver = observer; }

        synchronized void reset() {
            for (ProductState product : products.values()) product.reset();
            for (VariantState variant : variants.values()) variant.reset();
            mode = CommerceMode.NORMAL;
            batchObserver = (call, ids) -> { };
            batchCalls.set(0);
            captured.clear();
        }

        void setAllActivePrices(long price) {
            variants.values().forEach(variant -> { if ("ACTIVE".equals(variant.status)) variant.price = BigDecimal.valueOf(price); });
        }

        void adjustPrices(List<UUID> ids, long delta) {
            ids.stream().map(variants::get).filter(Objects::nonNull)
                    .forEach(variant -> variant.price = variant.price.add(BigDecimal.valueOf(delta)));
        }

        void setVariantStatus(List<UUID> ids, String status) {
            ids.stream().map(variants::get).filter(Objects::nonNull).forEach(variant -> variant.status = status);
        }

        void setCategory(List<UUID> ids, UUID categoryId) {
            productStatesFor(ids).forEach(product -> product.categoryId = categoryId);
        }

        void setDemographic(List<UUID> ids, String demographic) {
            productStatesFor(ids).forEach(product -> product.demographic = demographic);
        }

        void setCategoryActive(List<UUID> ids, boolean active) {
            productStatesFor(ids).forEach(product -> product.categoryActive = active);
        }

        void setBrandActive(List<UUID> ids, boolean active) {
            productStatesFor(ids).forEach(product -> product.brandActive = active);
        }

        void setProductStatus(List<UUID> ids, String status) {
            productStatesFor(ids).forEach(product -> product.status = status);
        }

        void setColors(List<UUID> ids, String color) {
            ids.stream().map(variants::get).filter(Objects::nonNull).forEach(variant -> variant.color = color);
        }

        private List<ProductState> productStatesFor(List<UUID> ids) {
            return ids.stream().map(variants::get).filter(Objects::nonNull)
                    .map(variant -> products.get(variant.productId)).filter(Objects::nonNull).distinct().toList();
        }

        private void batchLookup(HttpExchange exchange) throws IOException {
            JsonNode body = mapper.readTree(exchange.getRequestBody());
            List<UUID> ids = new ArrayList<>();
            body.path("variant_ids").forEach(node -> ids.add(UUID.fromString(node.asText())));
            capture(exchange, ids);
            int call = batchCalls.incrementAndGet();
            batchObserver.accept(call, List.copyOf(ids));
            switch (mode) {
                case HTTP_400 -> { respond(exchange, 400, mapper.createObjectNode()); return; }
                case HTTP_503 -> { respond(exchange, 503, mapper.createObjectNode()); return; }
                case SLOW -> {
                    try { Thread.sleep(1_200); }
                    catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                }
                case REDIRECT -> {
                    exchange.getResponseHeaders().add("Location", "http://127.0.0.1:"
                            + redirectTarget.getAddress().getPort() + "/redirect-target");
                    exchange.sendResponseHeaders(307, -1);
                    exchange.close();
                    return;
                }
                case MALFORMED_JSON -> {
                    respond(exchange, 200, "{not-json".getBytes(StandardCharsets.UTF_8));
                    return;
                }
                case MALFORMED_SHAPE -> {
                    respond(exchange, 200, mapper.createObjectNode().set("items", mapper.createObjectNode()));
                    return;
                }
                default -> { }
            }
            if (mode == CommerceMode.SLOW) return;

            ObjectNode response = mapper.createObjectNode();
            ArrayNode items = response.putArray("items");
            for (UUID id : ids) {
                VariantState variant = variants.get(id);
                if (isPubliclyAvailable(variant)) items.add(variantJson(variant));
            }
            if (mode == CommerceMode.DUPLICATE && items.size() > 0) items.add(items.get(0).deepCopy());
            if (mode == CommerceMode.UNREQUESTED) {
                VariantState extra = variants.values().iterator().next();
                ObjectNode extraJson = variantJson(extra);
                extraJson.put("variant_id", UUID.randomUUID().toString());
                items.add(extraJson);
            }
            if (mode == CommerceMode.WRONG_PRODUCT && items.size() > 0) {
                ((ObjectNode) items.get(0)).put("product_id", UUID.randomUUID().toString());
            }
            if (mode == CommerceMode.NON_INTEGER_PRICE && items.size() > 0) {
                ((ObjectNode) items.get(0)).put("unit_price_vnd", new BigDecimal("199000.5"));
            }
            respond(exchange, 200, response);
        }

        private void productDetail(HttpExchange exchange) throws IOException {
            String rawId = exchange.getRequestURI().getPath().substring("/api/v1/products/".length());
            UUID id;
            try { id = UUID.fromString(rawId); }
            catch (IllegalArgumentException exception) { respond(exchange, 400, mapper.createObjectNode()); return; }
            capture(exchange, List.of());
            ProductState product = products.get(id);
            if (mode == CommerceMode.PRODUCT_404) {
                respond(exchange, 404, mapper.createObjectNode());
                return;
            }
            if (mode == CommerceMode.PRODUCT_400) {
                respond(exchange, 400, mapper.createObjectNode());
                return;
            }
            if (mode == CommerceMode.PRODUCT_503) {
                respond(exchange, 503, mapper.createObjectNode());
                return;
            }
            if (!isPubliclyAvailable(product)) {
                respond(exchange, 404, mapper.createObjectNode());
                return;
            }
            if (mode == CommerceMode.HTTP_503) {
                respond(exchange, 503, mapper.createObjectNode());
                return;
            }
            ObjectNode response = mapper.createObjectNode()
                    .put("product_id", product.productId.toString())
                    .put("category_id", product.categoryId.toString())
                    .put("brand_id", product.brandId.toString())
                    .put("category_name", product.categoryName)
                    .put("brand_name", product.brandName)
                    .put("name", product.name)
                    .put("description", product.description)
                    .put("demographic_segment", product.demographic)
                    .put("product_status", product.status);
            ArrayNode productVariants = response.putArray("variants");
            variants.values().stream().filter(variant -> variant.productId.equals(product.productId)
                    && "ACTIVE".equals(variant.status)).forEach(variant -> {
                        ObjectNode variantJson = variantJson(variant);
                        if (mode == CommerceMode.DETAIL_PRICE_DRIFT) {
                            BigDecimal price = variant.price.add(BigDecimal.ONE);
                            variantJson.put("unit_price_vnd", price);
                        }
                        productVariants.add(variantJson);
                    });
            respond(exchange, 200, response);
        }

        private ObjectNode variantJson(VariantState variant) {
            ObjectNode result = mapper.createObjectNode()
                    .put("variant_id", variant.variantId.toString())
                    .put("product_id", variant.productId.toString())
                    .put("sku", variant.sku)
                    .put("size", variant.size)
                    .put("color", variant.color)
                    .put("variant_status", variant.status)
                    .put("available_quantity", 0);
            if (variant.material == null) result.putNull("material"); else result.put("material", variant.material);
            if (variant.price.scale() > 0) result.put("unit_price_vnd", variant.price);
            else result.put("unit_price_vnd", variant.price.longValueExact());
            ArrayNode images = result.putArray("images");
            ObjectNode image = images.addObject()
                    .put("image_id", UUID.nameUUIDFromBytes(("image:" + variant.variantId).getBytes(StandardCharsets.UTF_8)).toString())
                    .put("variant_id", variant.variantId.toString())
                    .put("image_url", "https://images.example.test/" + variant.variantId + ".jpg")
                    .put("display_order", 0)
                    .put("is_primary", true);
            image.put("alt_text", variant.color + " " + variant.size);
            return result;
        }

        private boolean isPubliclyAvailable(VariantState variant) {
            return variant != null && "ACTIVE".equals(variant.status)
                    && isPubliclyAvailable(products.get(variant.productId));
        }

        private static boolean isPubliclyAvailable(ProductState product) {
            return product != null && "ACTIVE".equals(product.status)
                    && product.categoryActive && product.brandActive;
        }

        private void capture(HttpExchange exchange, List<UUID> variantIds) {
            captured.add(new CapturedCommerceRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().containsKey("Authorization"),
                    exchange.getRequestHeaders().containsKey("X-StyleMind-Service-Token"), List.copyOf(variantIds)));
        }

        private void respond(HttpExchange exchange, int status, JsonNode body) throws IOException {
            respond(exchange, status, mapper.writeValueAsBytes(body));
        }

        private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }

        void close() {
            server.stop(0);
            redirectTarget.stop(0);
            executor.shutdownNow();
            targetExecutor.shutdownNow();
        }

        private static final class ProductState {
            private final UUID productId;
            private final UUID originalCategoryId;
            private UUID categoryId;
            private final String categoryName;
            private final UUID brandId;
            private final String brandName;
            private final String name;
            private final String description;
            private String demographic;
            private String status;
            private boolean categoryActive = true;
            private boolean brandActive = true;

            private ProductState(UUID productId, UUID categoryId, String categoryName, UUID brandId, String brandName,
                                 String name, String description, String demographic, String status) {
                this.productId = productId;
                this.originalCategoryId = categoryId;
                this.categoryId = categoryId;
                this.categoryName = categoryName;
                this.brandId = brandId;
                this.brandName = brandName;
                this.name = name;
                this.description = description;
                this.demographic = demographic;
                this.status = status;
            }

            private void reset() {
                categoryId = originalCategoryId;
                categoryActive = true;
                brandActive = true;
                status = "ACTIVE";
                demographic = "WOMEN";
            }
        }

        private static final class VariantState {
            private final UUID variantId;
            private final UUID productId;
            private final String sku;
            private final String size;
            private String color;
            private final String material;
            private final BigDecimal seedPrice;
            private BigDecimal price;
            private String status;

            private VariantState(UUID variantId, UUID productId, String sku, String size, String color,
                                 String material, Long price, String status) {
                this.variantId = variantId;
                this.productId = productId;
                this.sku = sku;
                this.size = size;
                this.color = color;
                this.material = material;
                this.seedPrice = BigDecimal.valueOf(price);
                this.price = seedPrice;
                this.status = status;
            }

            private void reset() {
                color = P04FixtureLoader.load().stream().filter(row -> row.variantId().equals(variantId))
                        .map(CatalogCandidate::colorRaw).findFirst().orElse(color);
                price = seedPrice;
                status = "ACTIVE";
            }
        }
    }
}
