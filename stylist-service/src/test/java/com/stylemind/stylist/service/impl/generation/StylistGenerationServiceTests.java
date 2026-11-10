package com.stylemind.stylist.service.impl.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stylemind.stylist.client.commerce.CommerceCatalogClient;
import com.stylemind.stylist.client.commerce.CommerceCatalogClient.HydratedVariant;
import com.stylemind.stylist.dto.generation.GenerateRecommendationsResponse;
import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import com.stylemind.stylist.model.generation.GenerationModel.Claim;
import com.stylemind.stylist.model.generation.GenerationModel;
import com.stylemind.stylist.model.generation.GenerationModel.Constraint;
import com.stylemind.stylist.model.generation.GenerationModel.ModelItemReason;
import com.stylemind.stylist.model.generation.GenerationModel.ModelOutput;
import com.stylemind.stylist.model.generation.GenerationModel.ModelRecommendation;
import com.stylemind.stylist.model.generation.GenerationModel.PersistedRecommendation;
import com.stylemind.stylist.model.generation.GenerationModel.RequestContext;
import com.stylemind.stylist.model.generation.GenerationModel.RequestRead;
import com.stylemind.stylist.model.generation.GenerationModel.RuntimeCandidate;
import com.stylemind.stylist.model.generation.GenerationModel.StoredItem;
import com.stylemind.stylist.model.generation.GenerationModel.StoredRecommendation;
import com.stylemind.stylist.service.catalog.ReviewedCatalogPolicy;
import com.stylemind.stylist.service.catalog.ReviewedCatalogPolicy.CategoryMapping;
import com.stylemind.stylist.service.catalog.ReviewedCatalogPolicy.Policy;
import com.stylemind.stylist.service.generation.GenerationPersistenceService;
import com.stylemind.stylist.service.generation.StructuredRecommendationModel;
import com.stylemind.stylist.service.retrieval.RuntimeEmbeddingPort;
import com.stylemind.stylist.service.knowledge.ReviewedKnowledgeService;
import com.stylemind.stylist.service.retrieval.RuntimeCandidateSearch;
import com.stylemind.stylist.repository.retrieval.RuntimeQdrantVectorStore;
import com.stylemind.stylist.mapper.recommendation.RecommendationMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class StylistGenerationServiceTests {
    private static final UUID CUSTOMER = id("10000000-0000-4000-8000-000000000001");
    private static final UUID REQUEST = id("20000000-0000-4000-8000-000000000001");
    private static final UUID SESSION = id("30000000-0000-4000-8000-000000000001");
    private static final UUID TOP_CATEGORY = id("40000000-0000-4000-8000-000000000001");
    private static final UUID BOTTOM_CATEGORY = id("40000000-0000-4000-8000-000000000002");
    private static final UUID BRAND = id("50000000-0000-4000-8000-000000000001");
    private static final UUID TOP_PRODUCT = id("60000000-0000-4000-8000-000000000001");
    private static final UUID BOTTOM_PRODUCT = id("60000000-0000-4000-8000-000000000002");
    private static final UUID TOP_VARIANT = id("70000000-0000-4000-8000-000000000001");
    private static final UUID BOTTOM_VARIANT = id("70000000-0000-4000-8000-000000000002");

    @Test
    void persistsOneCompleteShopOutfitAndTerminalReplayDoesNotCallModelAgain() {
        Fixture f = new Fixture(null, null, null, null);
        try {
            var response = f.service.generate(CUSTOMER, REQUEST);
            assertThat(response.requestStatus()).isEqualTo("SUCCEEDED");
            assertThat(response.recommendations()).hasSize(1);
            assertThat(response.recommendations().getFirst().items()).hasSize(2);
            assertThat(response.recommendations().getFirst().items())
                    .extracting(item -> item.slotName()).containsExactly("TOP", "BOTTOM");
            assertThat(response.recommendations().getFirst().items())
                    .extracting(item -> item.shopVariantId()).containsExactly(TOP_VARIANT, BOTTOM_VARIANT);
            assertThat(f.persisted.get()).hasSize(1);
            assertThat(f.persisted.get().getFirst().items()).hasSize(2);
            assertThat(f.persisted.get().getFirst().items())
                    .extracting(item -> item.displayOrder()).containsExactly(1, 2);

            var replay = f.service.generate(CUSTOMER, REQUEST);
            assertThat(replay.requestStatus()).isEqualTo("SUCCEEDED");
            assertThat(replay.recommendations()).hasSize(1);
            verify(f.model, times(1)).select(any(), anyList(), anyList(), any(Duration.class));
            verify(f.persistence, times(1)).claim(CUSTOMER, REQUEST);
            verify(f.persistence, times(1)).complete(eq(CUSTOMER), eq(REQUEST), eq("SUCCEEDED"), anyList());
        } finally { f.service.stopRemoteExecutor(); }
    }

    @Test
    void concurrentCallsHaveOneClaimAndOneModelInvocation() throws Exception {
        CountDownLatch readyEntered = new CountDownLatch(2);
        CountDownLatch releaseReady = new CountDownLatch(1);
        CountDownLatch modelEntered = new CountDownLatch(1);
        CountDownLatch releaseModel = new CountDownLatch(1);
        Fixture f = new Fixture(readyEntered, releaseReady, modelEntered, releaseModel);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Object> first = CompletableFuture.supplyAsync(() -> f.invoke(), callers);
            CompletableFuture<Object> second = CompletableFuture.supplyAsync(() -> f.invoke(), callers);
            assertThat(readyEntered.await(5, TimeUnit.SECONDS)).isTrue();
            releaseReady.countDown();
            assertThat(modelEntered.await(5, TimeUnit.SECONDS)).isTrue();

            // The losing request finishes while the winning request is held at the model boundary.
            Object loser = CompletableFuture.anyOf(first, second).get(5, TimeUnit.SECONDS);
            releaseModel.countDown();
            Object firstResult = first.get(5, TimeUnit.SECONDS);
            Object secondResult = second.get(5, TimeUnit.SECONDS);
            List<Object> outcomes = List.of(firstResult, secondResult);
            assertThat(outcomes).filteredOn(value -> value instanceof GenerateRecommendationsResponse)
                    .hasSize(1);
            assertThat(outcomes).filteredOn(value -> value instanceof StylistApiException)
                    .singleElement().satisfies(value -> assertThat(((StylistApiException) value).errorCode())
                            .isEqualTo(ErrorCode.GENERATION_IN_PROGRESS));
            assertThat(loser).isInstanceOf(StylistApiException.class);
            verify(f.model, times(1)).select(any(), anyList(), anyList(), any(Duration.class));
            verify(f.persistence, times(2)).claim(CUSTOMER, REQUEST);
            verify(f.persistence, times(1)).complete(eq(CUSTOMER), eq(REQUEST), eq("SUCCEEDED"), anyList());
        } finally {
            releaseReady.countDown();
            releaseModel.countDown();
            callers.shutdownNow();
            f.service.stopRemoteExecutor();
        }
    }

    @Test
    void generatedAtUsesPostgresTimestampPrecisionBeforeReturningPersistedResponse() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-07T07:22:18.377565987Z"), ZoneOffset.UTC);
        Fixture f = new Fixture(null, null, null, null, clock);
        try {
            var response = f.service.generate(CUSTOMER, REQUEST);

            assertThat(response.recommendations().getFirst().generatedAt())
                    .isEqualTo(Instant.parse("2026-11-07T07:22:18.377565Z"));
            assertThat(f.persisted.get().getFirst().generatedAt())
                    .isEqualTo(response.recommendations().getFirst().generatedAt());
        } finally {
            f.service.stopRemoteExecutor();
        }
    }

    @Test
    void missingColorCannotSatisfyAHardColorExclusionByAssumption() {
        Fixture f = new Fixture(null, null, null, null);
        RequestContext constrained = new RequestContext(REQUEST, SESSION, "RECEIVED", "Need a work outfit",
                "work", null, "minimal", null, 850_000L, true, false,
                List.of(new Constraint("EXCLUDE_COLOR", null, "blue", true)));
        when(f.persistence.readOwned(CUSTOMER, REQUEST)).thenReturn(new RequestRead(constrained, List.of()));
        when(f.commerce.hydrate(anyMap(), any(Duration.class))).thenReturn(Map.of(
                TOP_VARIANT, new HydratedVariant(TOP_PRODUCT, TOP_VARIANT, TOP_CATEGORY, BRAND, "Áo sơ mi", "Áo",
                        "StyleMind", "WOMEN", "ACTIVE", "ACTIVE", null, "M", null, 400_000L, List.of()),
                BOTTOM_VARIANT, Fixture.hydrated(BOTTOM_PRODUCT, BOTTOM_VARIANT, BOTTOM_CATEGORY,
                        "Quần dài", "BOTTOM", "black", 450_000L)));
        when(f.persistence.complete(CUSTOMER, REQUEST, "NO_RESULT", List.of())).thenReturn(List.of());

        try {
            var result = f.service.generate(CUSTOMER, REQUEST);

            assertThat(result.requestStatus()).isEqualTo("NO_RESULT");
            assertThat(result.recommendations()).isEmpty();
            verify(f.model, never()).select(any(), anyList(), anyList(), any(Duration.class));
            verify(f.persistence).complete(CUSTOMER, REQUEST, "NO_RESULT", List.of());
        } finally {
            f.service.stopRemoteExecutor();
        }
    }

    @Test
    void finalCommerceProtocolFailureMarksRequestFailedWithoutPersistingRecommendations() {
        Fixture f = new Fixture(null, null, null, null);
        Map<UUID, HydratedVariant> initial = Map.of(
                TOP_VARIANT, Fixture.hydrated(TOP_PRODUCT, TOP_VARIANT, TOP_CATEGORY, "Áo sơ mi", "TOP", "blue", 400_000),
                BOTTOM_VARIANT, Fixture.hydrated(BOTTOM_PRODUCT, BOTTOM_VARIANT, BOTTOM_CATEGORY,
                        "Quần dài", "BOTTOM", "black", 450_000));
        when(f.commerce.hydrate(anyMap(), any(Duration.class))).thenReturn(initial)
                .thenThrow(new StylistApiException(ErrorCode.DEPENDENCY_PROTOCOL_ERROR));
        doAnswer(invocation -> {
            f.status.set("FAILED");
            return null;
        }).when(f.persistence).markFailed(CUSTOMER, REQUEST);

        try {
            assertThatThrownBy(() -> f.service.generate(CUSTOMER, REQUEST))
                    .isInstanceOf(StylistApiException.class)
                    .satisfies(exception -> assertThat(((StylistApiException) exception).errorCode())
                            .isEqualTo(ErrorCode.DEPENDENCY_PROTOCOL_ERROR));

            assertThat(f.status.get()).isEqualTo("FAILED");
            assertThat(f.persisted.get()).isEmpty();
            verify(f.model).select(any(), anyList(), anyList(), any(Duration.class));
            verify(f.persistence, never()).complete(eq(CUSTOMER), eq(REQUEST), anyString(), anyList());
            verify(f.persistence).markFailed(CUSTOMER, REQUEST);
        } finally {
            f.service.stopRemoteExecutor();
        }
    }

    private static UUID id(String value) { return UUID.fromString(value); }

    private static final class Fixture {
        private final GenerationPersistenceService persistence = mock(GenerationPersistenceService.class);
        private final ReviewedCatalogPolicy policyLoader = mock(ReviewedCatalogPolicy.class);
        private final RuntimeEmbeddingPort embeddings = mock(RuntimeEmbeddingPort.class);
        private final RuntimeCandidateSearch search = mock(RuntimeCandidateSearch.class);
        private final CommerceCatalogClient commerce = mock(CommerceCatalogClient.class);
        private final ReviewedKnowledgeService knowledge = mock(ReviewedKnowledgeService.class);
        private final StructuredRecommendationModel model = mock(StructuredRecommendationModel.class);
        private final RuntimeQdrantVectorStore qdrant = mock(RuntimeQdrantVectorStore.class);
        private final Policy policy = new Policy("reviewed-test-policy-v1", "reviewer",
                Map.of(TOP_CATEGORY, new CategoryMapping(TOP_CATEGORY, "TOP", "WOMEN", "reviewer"),
                        BOTTOM_CATEGORY, new CategoryMapping(BOTTOM_CATEGORY, "BOTTOM", "WOMEN", "reviewer")),
                Map.of("blue", "blue", "black", "black"), Set.of("blue", "black"));
        private final AtomicReference<String> status = new AtomicReference<>("RECEIVED");
        private final AtomicReference<List<StoredRecommendation>> stored = new AtomicReference<>(List.of());
        private final AtomicReference<List<PersistedRecommendation>> persisted = new AtomicReference<>(List.of());
        private final AtomicInteger claimCalls = new AtomicInteger();
        private final CountDownLatch readyEntered;
        private final CountDownLatch releaseReady;
        private final CountDownLatch modelEntered;
        private final CountDownLatch releaseModel;
        private final StylistGenerationServiceImpl service;

        private Fixture(CountDownLatch readyEntered, CountDownLatch releaseReady,
                        CountDownLatch modelEntered, CountDownLatch releaseModel) {
            this(readyEntered, releaseReady, modelEntered, releaseModel, Clock.systemUTC());
        }

        private Fixture(CountDownLatch readyEntered, CountDownLatch releaseReady,
                        CountDownLatch modelEntered, CountDownLatch releaseModel, Clock clock) {
            this.readyEntered = readyEntered;
            this.releaseReady = releaseReady;
            this.modelEntered = modelEntered;
            this.releaseModel = releaseModel;
            configure();
            service = new StylistGenerationServiceImpl(persistence, policyLoader, embeddings, search, commerce,
                    knowledge, model, clock, qdrant, new RecommendationMapper(), 10_000);
        }

        private void configure() {
            when(policyLoader.require()).thenReturn(policy);
            when(embeddings.isConfigured()).thenReturn(true);
            when(embeddings.embedQuery(anyString())).thenReturn(new float[RuntimeQdrantVectorStore.DIMENSIONS]);
            when(model.isConfigured()).thenReturn(true);
            when(knowledge.isConfigured()).thenReturn(true);
            when(qdrant.isConfigured()).thenReturn(true);
            when(qdrant.isReady(eq(policy), any(Duration.class))).thenAnswer(invocation -> {
                if (readyEntered != null) {
                    readyEntered.countDown();
                    if (!releaseReady.await(5, TimeUnit.SECONDS)) throw new AssertionError("readiness barrier expired");
                }
                return true;
            });
            when(knowledge.findApprovedFacts(any(RequestContext.class), anyList(), any(Duration.class)))
                    .thenReturn(List.of());
            when(search.search(any(float[].class), eq("TOP"), eq(List.of(TOP_CATEGORY)), eq(20),
                    eq(policy.revision()), any(Duration.class))).thenReturn(List.of(candidate(TOP_PRODUCT,
                    TOP_VARIANT, TOP_CATEGORY, "TOP", "Áo sơ mi")));
            when(search.search(any(float[].class), eq("BOTTOM"), eq(List.of(BOTTOM_CATEGORY)), eq(20),
                    eq(policy.revision()), any(Duration.class))).thenReturn(List.of(candidate(BOTTOM_PRODUCT,
                    BOTTOM_VARIANT, BOTTOM_CATEGORY, "BOTTOM", "Quần dài")));
            Map<UUID, HydratedVariant> current = Map.of(
                    TOP_VARIANT, hydrated(TOP_PRODUCT, TOP_VARIANT, TOP_CATEGORY, "Áo sơ mi", "TOP", "blue", 400_000),
                    BOTTOM_VARIANT, hydrated(BOTTOM_PRODUCT, BOTTOM_VARIANT, BOTTOM_CATEGORY, "Quần dài", "BOTTOM", "black", 450_000));
            when(commerce.hydrate(anyMap(), any(Duration.class))).thenReturn(current);
            when(model.select(any(RequestContext.class), anyList(), anyList(), any(Duration.class)))
                    .thenAnswer(invocation -> {
                        if (modelEntered != null) {
                            modelEntered.countDown();
                            if (!releaseModel.await(5, TimeUnit.SECONDS)) throw new AssertionError("model barrier expired");
                        }
                        List<GenerationModel.FrozenOutfit> whitelist =
                                invocation.getArgument(1);
                        var outfit = whitelist.getFirst();
                        var reasons = outfit.items().stream().map(item -> new ModelItemReason(item.alias(),
                                "Reason from catalog evidence.")).toList();
                        return new ModelOutput(List.of(new ModelRecommendation(outfit.alias(),
                                "Explanation from allowed evidence.", null, reasons)));
                    });
            when(persistence.readOwned(CUSTOMER, REQUEST)).thenAnswer(invocation ->
                    new RequestRead(context(status.get()), stored.get()));
            when(persistence.claim(CUSTOMER, REQUEST)).thenAnswer(invocation -> {
                claimCalls.incrementAndGet();
                boolean acquired = status.compareAndSet("RECEIVED", "PROCESSING");
                return new Claim(status.get(), acquired);
            });
            when(persistence.complete(eq(CUSTOMER), eq(REQUEST), eq("SUCCEEDED"), anyList()))
                    .thenAnswer(invocation -> {
                        List<PersistedRecommendation> rows = invocation.getArgument(3);
                        persisted.set(List.copyOf(rows));
                        status.set("SUCCEEDED");
                        stored.set(rows.stream().map(Fixture::toStored).toList());
                        return stored.get();
                    });
        }

        private Object invoke() {
            try { return service.generate(CUSTOMER, REQUEST); }
            catch (RuntimeException exception) { return exception; }
        }

        private static RequestContext context(String status) {
            return new RequestContext(REQUEST, SESSION, status, "Need an office outfit", "work", null,
                    "minimal", null, 850_000L, true, false, List.<Constraint>of());
        }

        private static RuntimeCandidate candidate(UUID product, UUID variant, UUID category, String slot, String name) {
            return new RuntimeCandidate(product, variant, category, slot.equals("TOP") ? "Áo" : "Quần", slot,
                    "WOMEN", name, null, slot.equals("TOP") ? "blue" : "black", "M", "cotton", 0.9);
        }

        private static HydratedVariant hydrated(UUID product, UUID variant, UUID category, String name,
                                                 String slot, String color, long price) {
            return new HydratedVariant(product, variant, category, BRAND, name, slot.equals("TOP") ? "Áo" : "Quần",
                    "StyleMind", "WOMEN", "ACTIVE", "ACTIVE", color, "M", "cotton", price, List.of());
        }

        private static StoredRecommendation toStored(PersistedRecommendation recommendation) {
            List<StoredItem> items = recommendation.items().stream().map(item -> new StoredItem("SHOP_VARIANT",
                    null, item.shopVariantId(), item.slot(), item.reason(), item.displayOrder())).toList();
            return new StoredRecommendation(recommendation.recommendationId(), REQUEST, recommendation.explanation(),
                    recommendation.stylingTips(), recommendation.generatedAt(), items);
        }
    }
}
