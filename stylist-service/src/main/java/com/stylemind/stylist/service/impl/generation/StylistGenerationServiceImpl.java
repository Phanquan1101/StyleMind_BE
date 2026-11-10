package com.stylemind.stylist.service.impl.generation;

import com.stylemind.stylist.client.commerce.CommerceCatalogClient;
import com.stylemind.stylist.client.commerce.CommerceCatalogClient.HydratedVariant;
import com.stylemind.stylist.dto.generation.GenerateRecommendationsResponse;
import com.stylemind.stylist.dto.generation.RecommendationDetailResponse;
import com.stylemind.stylist.dto.generation.RequestRecommendationsResponse;
import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import com.stylemind.stylist.model.generation.GenerationModel.Constraint;
import com.stylemind.stylist.model.generation.GenerationModel.FrozenOutfit;
import com.stylemind.stylist.model.generation.GenerationModel.ItemAlias;
import com.stylemind.stylist.model.generation.GenerationModel.ModelOutput;
import com.stylemind.stylist.model.generation.GenerationModel.ModelRecommendation;
import com.stylemind.stylist.model.generation.GenerationModel.PersistedItem;
import com.stylemind.stylist.model.generation.GenerationModel.PersistedRecommendation;
import com.stylemind.stylist.model.generation.GenerationModel.RequestContext;
import com.stylemind.stylist.model.generation.GenerationModel.RequestRead;
import com.stylemind.stylist.model.generation.GenerationModel.RuntimeCandidate;
import com.stylemind.stylist.model.generation.GenerationModel.StoredItem;
import com.stylemind.stylist.model.generation.GenerationModel.StoredRecommendation;
import com.stylemind.stylist.model.recommendation.EngineModel.Evidence;
import com.stylemind.stylist.model.recommendation.EngineModel.Exclusion;
import com.stylemind.stylist.model.recommendation.EngineModel.Input;
import com.stylemind.stylist.model.recommendation.EngineModel.Item;
import com.stylemind.stylist.model.recommendation.EngineModel.Preferences;
import com.stylemind.stylist.model.recommendation.EngineModel.Result;
import com.stylemind.stylist.model.recommendation.EngineModel.ShopVariantReference;
import com.stylemind.stylist.model.recommendation.EngineModel.Slot;
import com.stylemind.stylist.model.recommendation.EngineModel.Vocabulary;
import com.stylemind.stylist.model.recommendation.EngineModel;
import com.stylemind.stylist.mapper.recommendation.RecommendationMapper;
import com.stylemind.stylist.service.catalog.ReviewedCatalogPolicy;
import com.stylemind.stylist.service.catalog.ReviewedCatalogPolicy.Policy;
import com.stylemind.stylist.service.generation.GenerationPersistenceService;
import com.stylemind.stylist.service.generation.StructuredRecommendationModel;
import com.stylemind.stylist.service.generation.StylistGenerationService;
import com.stylemind.stylist.service.knowledge.ReviewedKnowledgeService;
import com.stylemind.stylist.service.knowledge.ReviewedKnowledgeService.Fact;
import com.stylemind.stylist.service.recommendation.internal.OutfitEngine;
import com.stylemind.stylist.service.retrieval.RuntimeCandidateSearch;
import com.stylemind.stylist.service.retrieval.RuntimeEmbeddingPort;
import com.stylemind.stylist.repository.retrieval.RuntimeQdrantVectorStore;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;
import java.util.concurrent.Callable;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/** The one runtime generation flow; all network work happens outside persistence transactions. */
@Service
public class StylistGenerationServiceImpl implements StylistGenerationService {
    private static final Logger log = LoggerFactory.getLogger(StylistGenerationServiceImpl.class);
    private static final int RETRIEVAL_LIMIT_PER_SLOT = 20;
    private static final Duration MIN_PROVIDER_CALL_BUDGET = Duration.ofSeconds(8);
    private static final String NO_RESULT_BOUNDED = "BOUNDED_SEARCH_NO_OUTFIT";
    private static final String NO_RESULT_CATALOG_CHANGED = "CATALOG_CHANGED_DURING_GENERATION";

    private final GenerationPersistenceService persistence;
    private final ReviewedCatalogPolicy catalogPolicy;
    private final RuntimeEmbeddingPort embeddings;
    private final RuntimeCandidateSearch candidateSearch;
    private final CommerceCatalogClient commerce;
    private final ReviewedKnowledgeService knowledge;
    private final StructuredRecommendationModel model;
    private final OutfitEngine engine = new OutfitEngine();
    private final Clock clock;
    private final RuntimeQdrantVectorStore qdrant;
    private final Duration deadlineDuration;
    private final RecommendationMapper mapper;
    private final GenerationOutputValidator outputValidator = new GenerationOutputValidator();
    private final GenerationDeadlineExecutor deadlineExecutor = new GenerationDeadlineExecutor();

    public StylistGenerationServiceImpl(GenerationPersistenceService persistence,
            ReviewedCatalogPolicy catalogPolicy, RuntimeEmbeddingPort embeddings,
            RuntimeCandidateSearch candidateSearch, CommerceCatalogClient commerce,
            ReviewedKnowledgeService knowledge, StructuredRecommendationModel model, Clock clock,
            RuntimeQdrantVectorStore qdrant, RecommendationMapper mapper,
            @Value("${stylemind.stylist.generation.deadline-ms:30000}") long deadlineMillis) {
        this.persistence = persistence;
        this.catalogPolicy = catalogPolicy;
        this.embeddings = embeddings;
        this.candidateSearch = candidateSearch;
        this.commerce = commerce;
        this.knowledge = knowledge;
        this.model = model;
        this.clock = clock;
        this.qdrant = qdrant;
        this.mapper = mapper;
        if (deadlineMillis < 1 || deadlineMillis > 30_000) {
            throw new IllegalStateException("Stylist generation deadline must be between 1 and 30000 milliseconds");
        }
        this.deadlineDuration = Duration.ofMillis(deadlineMillis);
    }

    @Override
    public GenerateRecommendationsResponse generate(UUID customerId, UUID requestId) {
        long deadline = System.nanoTime() + deadlineDuration.toNanos();
        RequestRead observed = safeReadOwned(customerId, requestId);
        RequestContext context = observed.context();
        if (isTerminal(context.status())) return generateResponse(requestId, context.status(), observed.recommendations(),
                limitations(context.status(), List.of()));
        if ("PROCESSING".equals(context.status())) throw new StylistApiException(ErrorCode.GENERATION_IN_PROGRESS, requestId);
        if ("FAILED".equals(context.status())) throw new StylistApiException(ErrorCode.GENERATION_ALREADY_ATTEMPTED, requestId);
        if (!"RECEIVED".equals(context.status())) throw new StylistApiException(ErrorCode.GENERATION_ALREADY_ATTEMPTED, requestId);
        if (context.sessionClosed()) throw new StylistApiException(ErrorCode.SESSION_CLOSED, requestId);

        Policy policy = catalogPolicy.require();
        List<Exclusion> exclusions = exclusions(context, policy, requestId);
        runBeforeDeadline(() -> {
            requireRuntimeReady(policy, deadline);
            return null;
        }, deadline);
        ensureTime(deadline);

        var claim = persistence.claim(customerId, requestId);
        if (!claim.acquired()) {
            if (isTerminal(claim.status())) {
                RequestRead terminal = safeReadOwned(customerId, requestId);
                return generateResponse(requestId, terminal.context().status(), terminal.recommendations(),
                        limitations(terminal.context().status(), List.of()));
            }
            if ("PROCESSING".equals(claim.status())) {
                throw new StylistApiException(ErrorCode.GENERATION_IN_PROGRESS, requestId);
            }
            throw new StylistApiException(ErrorCode.GENERATION_ALREADY_ATTEMPTED, requestId);
        }

        try {
            ExecutionResult execution = runBeforeDeadline(
                    () -> execute(context, policy, exclusions, deadline), deadline);
            // The remote worker never persists. Only this request thread may finalize, after the deadline check.
            ensureTime(deadline);
            List<StoredRecommendation> stored = persistence.complete(customerId, requestId, execution.status(),
                    execution.recommendations());
            return generateResponse(requestId, execution.status(), stored, execution.limitations());
        } catch (RuntimeException failure) {
            RequestRead committed = readTerminalIfCommitted(customerId, requestId);
            if (committed != null) return generateResponse(requestId, committed.context().status(),
                    committed.recommendations(), limitations(committed.context().status(), List.of()));
            try {
                persistence.markFailed(customerId, requestId);
            } catch (RuntimeException ignored) {
                // If the database is unavailable, PROCESSING may remain; the operator runbook documents this case.
            }
            throw withRequestId(failure, requestId);
        }
    }

    @Override
    public RequestRecommendationsResponse readRequestRecommendations(UUID customerId, UUID requestId) {
        RequestRead read = safeReadOwned(customerId, requestId);
        return mapper.requestRecommendations(requestId, read.context().status(), read.recommendations());
    }

    @Override
    public RecommendationDetailResponse readRecommendation(UUID customerId, UUID recommendationId) {
        StoredRecommendation stored;
        try {
            stored = persistence.readOwnedRecommendation(customerId, recommendationId);
        } catch (DataAccessException exception) {
            throw new StylistApiException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        }
        List<StoredItem> shopItems = stored.items().stream()
                .filter(item -> "SHOP_VARIANT".equals(item.sourceType())).toList();
        Map<UUID, HydratedVariant> current = shopItems.isEmpty() ? Map.of()
                : commerce.hydrateCurrentVariants(shopItems.stream().map(StoredItem::shopVariantId).toList());
        List<RecommendationDetailResponse.Item> items = new ArrayList<>();
        long total = 0;
        boolean allCurrent = !stored.items().isEmpty();
        for (StoredItem item : stored.items()) {
            if (!"SHOP_VARIANT".equals(item.sourceType())) {
                allCurrent = false;
                items.add(mapper.unavailable(item));
                continue;
            }
            HydratedVariant variant = current.get(item.shopVariantId());
            if (variant == null || !"ACTIVE".equals(variant.variantStatus())
                    || !"ACTIVE".equals(variant.productStatus())) {
                allCurrent = false;
                items.add(mapper.unavailable(item));
                continue;
            }
            try { total = Math.addExact(total, variant.unitPriceVnd()); }
            catch (ArithmeticException exception) { allCurrent = false; }
            items.add(mapper.available(item, variant));
        }
        Long currentTotal = allCurrent ? total : null;
        return new RecommendationDetailResponse(stored.recommendationId(), stored.stylingRequestId(),
                stored.explanation(), stored.stylingTips(), stored.generatedAt(), currentTotal,
                "CURRENT_COMMERCE_DATA_NOT_HISTORICAL", items);
    }

    private ExecutionResult execute(RequestContext context, Policy policy, List<Exclusion> exclusions, long deadline) {
        String query = retrievalText(context);
        CandidateSet candidates = retrieveAndHydrate(query, policy, exclusions, deadline);
        List<String> providerFacts = loadProviderFacts(context, candidates.currentByVariant(), deadline);
        Result engineResult = recommend(context, policy, exclusions, candidates.engineItems());
        if (engineResult.noResult()) {
            return new ExecutionResult("NO_RESULT", List.of(), List.of(NO_RESULT_BOUNDED));
        }

        List<FrozenOutfit> whitelist = outputValidator.freeze(engineResult.outfits(), candidates.currentByVariant());
        List<ModelRecommendation> selected = selectRecommendations(context, whitelist, providerFacts, deadline);
        return recheckAndBuildRecommendations(context, policy, exclusions, whitelist, selected, deadline);
    }

    private CandidateSet retrieveAndHydrate(String query, Policy policy, List<Exclusion> exclusions, long deadline) {
        ensureTime(deadline, MIN_PROVIDER_CALL_BUDGET);
        float[] vector = embeddings.embedQuery(query);
        ensureTime(deadline);

        List<RuntimeCandidate> hits = new ArrayList<>();
        hits.addAll(search(vector, "TOP", policy.categoryIdsForSlot("TOP"), policy, deadline));
        hits.addAll(search(vector, "BOTTOM", policy.categoryIdsForSlot("BOTTOM"), policy, deadline));
        Map<UUID, String> expectedSlots = new LinkedHashMap<>();
        Map<UUID, UUID> productsByVariant = new LinkedHashMap<>();
        for (RuntimeCandidate hit : hits) {
            String previous = expectedSlots.putIfAbsent(hit.variantId(), hit.slot());
            UUID previousProduct = productsByVariant.putIfAbsent(hit.variantId(), hit.productId());
            if (previous != null || previousProduct != null) throw dependencyProtocol();
        }

        ensureTime(deadline);
        Map<UUID, HydratedVariant> hydrated = productsByVariant.isEmpty() ? Map.of()
                : commerce.hydrate(productsByVariant, remaining(deadline));
        ensureTime(deadline);
        List<Item> engineItems = new ArrayList<>();
        Map<UUID, HydratedVariant> currentByVariant = new HashMap<>();
        for (RuntimeCandidate hit : hits) {
            HydratedVariant current = hydrated.get(hit.variantId());
            if (current == null || !"ACTIVE".equals(current.productStatus())
                    || !"ACTIVE".equals(current.variantStatus()) || !"WOMEN".equals(current.demographicSegment())
                    || !hit.productId().equals(current.productId()) || !hit.categoryId().equals(current.categoryId())) {
                continue;
            }
            var mapping = policy.slot(current.categoryId(), current.demographicSegment());
            if (mapping.isEmpty() || !mapping.orElseThrow().equals(expectedSlots.get(hit.variantId()))) continue;
            if (!canVerifyHardColorExclusion(current, exclusions, policy)) continue;
            engineItems.add(GenerationEngineInputMapper.item(current, mapping.orElseThrow(), policy));
            currentByVariant.put(current.variantId(), current);
        }
        return new CandidateSet(engineItems, currentByVariant);
    }

    private List<String> loadProviderFacts(RequestContext context, Map<UUID, HydratedVariant> currentByVariant,
                                           long deadline) {
        List<String> candidateAttributes = GenerationEngineInputMapper.candidateAttributes(currentByVariant.values());
        ensureTime(deadline);
        List<Fact> evidence = knowledge.findApprovedFacts(context, candidateAttributes, remaining(deadline));
        ensureTime(deadline);
        return evidence.stream().limit(10).map(Fact::text).toList();
    }

    private Result recommend(RequestContext context, Policy policy, List<Exclusion> exclusions,
                             List<Item> engineItems) {
        try {
            return engine.recommend(new Input(engineItems, List.of(), context.maxBudgetVnd(),
                    context.hardBudget(), exclusions, new Preferences(Set.of(), Set.of(), Set.of()),
                    new Vocabulary(Set.of(), policy.canonicalColors(), Set.of()),
                    new Evidence(Set.of(), Set.of())));
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new StylistApiException(ErrorCode.INVALID_CONTEXT);
        }
    }

    private List<ModelRecommendation> selectRecommendations(RequestContext context, List<FrozenOutfit> whitelist,
                                                             List<String> providerFacts, long deadline) {
        ensureTime(deadline, MIN_PROVIDER_CALL_BUDGET);
        ModelOutput output = model.select(context, whitelist, providerFacts, remaining(deadline));
        ensureTime(deadline);
        return outputValidator.validate(output, whitelist);
    }

    private ExecutionResult recheckAndBuildRecommendations(RequestContext context, Policy policy,
            List<Exclusion> exclusions, List<FrozenOutfit> whitelist,
            List<ModelRecommendation> selected, long deadline) {
        Map<UUID, UUID> selectedProducts = new LinkedHashMap<>();
        for (ModelRecommendation recommendation : selected) {
            FrozenOutfit outfit = outputValidator.find(whitelist, recommendation.outfitAlias());
            for (ItemAlias item : outfit.items()) selectedProducts.put(item.variantId(), item.productId());
        }
        ensureTime(deadline);
        Map<UUID, HydratedVariant> finalCurrent = commerce.hydrate(selectedProducts, remaining(deadline));
        ensureTime(deadline);

        List<PersistedRecommendation> persisted = new ArrayList<>();
        for (ModelRecommendation recommendation : selected) {
            FrozenOutfit outfit = outputValidator.find(whitelist, recommendation.outfitAlias());
            List<PersistedItem> items = finalItems(outfit, recommendation, finalCurrent, policy, exclusions, context);
            if (items != null) {
                persisted.add(new PersistedRecommendation(UUID.randomUUID(), recommendation.explanation(),
                        recommendation.stylingTips(), Instant.now(clock).truncatedTo(ChronoUnit.MICROS), items));
            }
        }
        if (persisted.isEmpty()) {
            return new ExecutionResult("NO_RESULT", List.of(), List.of(NO_RESULT_CATALOG_CHANGED));
        }
        return new ExecutionResult("SUCCEEDED", List.copyOf(persisted), List.of());
    }

    private List<RuntimeCandidate> search(float[] vector, String slot, List<UUID> categories,
                                          Policy policy, long deadline) {
        if (categories.isEmpty()) throw new StylistApiException(ErrorCode.GENERATION_RUNTIME_UNCONFIGURED);
        ensureTime(deadline);
        List<RuntimeCandidate> hits = candidateSearch.search(vector, slot, categories,
                RETRIEVAL_LIMIT_PER_SLOT, policy.revision(), remaining(deadline));
        ensureTime(deadline);
        if (hits == null || hits.size() > RETRIEVAL_LIMIT_PER_SLOT) throw dependencyProtocol();
        Set<UUID> seen = new HashSet<>();
        for (RuntimeCandidate hit : hits) {
            if (hit == null || hit.variantId() == null || hit.productId() == null || hit.categoryId() == null
                    || !slot.equals(hit.slot()) || !"WOMEN".equals(hit.demographicSegment())
                    || !Double.isFinite(hit.retrievalScore()) || !seen.add(hit.variantId())
                    || !categories.contains(hit.categoryId())) throw dependencyProtocol();
            if (policy.slot(hit.categoryId(), hit.demographicSegment()).filter(slot::equals).isEmpty()) {
                throw dependencyProtocol();
            }
        }
        return hits;
    }

    private void requireRuntimeReady(Policy policy, long deadline) {
        if (!embeddings.isConfigured() || !model.isConfigured() || !knowledge.isConfigured()
                || !qdrant.isConfigured()) {
            throw new StylistApiException(ErrorCode.GENERATION_RUNTIME_UNCONFIGURED);
        }
        if (policy.categoryIdsForSlot("TOP").isEmpty() || policy.categoryIdsForSlot("BOTTOM").isEmpty()) {
            throw new StylistApiException(ErrorCode.GENERATION_RUNTIME_UNCONFIGURED);
        }
        if (!qdrant.isReady(policy, remaining(deadline))) {
            throw new StylistApiException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        }
        ensureTime(deadline);
        knowledge.assertReady(remaining(deadline));
        ensureTime(deadline);
    }

    private static List<Exclusion> exclusions(RequestContext context, Policy policy, UUID requestId) {
        List<Exclusion> exclusions = new ArrayList<>();
        for (Constraint constraint : context.constraints()) {
            try {
                if ("EXCLUDE_COLOR".equals(constraint.kind())) {
                    if (constraint.catalogEntityId() != null || constraint.textValue() == null
                            || constraint.textValue().isBlank()) throw invalidContext();
                    String raw = constraint.textValue().trim();
                    String canonical = policy.color(raw).orElse(null);
                    if (constraint.hard() && canonical == null) {
                        log.warn("Generation preflight rejected correlationId={} requestId={} sessionId={} "
                                        + "endpoint=/api/v1/stylist/requests/{requestId}/generate status={} "
                                        + "errorCode={} reason=UNMAPPED_HARD_COLOR",
                                MDC.get("correlationId"), requestId, context.sessionId(),
                                ErrorCode.INVALID_CONTEXT.status().value(), ErrorCode.INVALID_CONTEXT);
                        throw new StylistApiException(ErrorCode.INVALID_CONTEXT, requestId);
                    }
                    exclusions.add(new Exclusion(constraint.kind(), null,
                            canonical == null ? raw.toLowerCase(Locale.ROOT) : canonical, constraint.hard()));
                } else {
                    if (constraint.catalogEntityId() == null || constraint.textValue() != null) throw invalidContext();
                    exclusions.add(new Exclusion(constraint.kind(), constraint.catalogEntityId(), null, constraint.hard()));
                }
            } catch (IllegalArgumentException exception) {
                throw invalidContext();
            }
        }
        return List.copyOf(exclusions);
    }

    private List<PersistedItem> finalItems(FrozenOutfit outfit, ModelRecommendation recommendation,
            Map<UUID, HydratedVariant> current, Policy policy, List<Exclusion> exclusions, RequestContext context) {
        Map<String, String> reasons = new HashMap<>();
        recommendation.itemReasons().forEach(reason -> reasons.put(reason.itemAlias(), reason.reason()));
        List<PersistedItem> items = new ArrayList<>(2);
        long total = 0;
        for (ItemAlias alias : outfit.items()) {
            HydratedVariant now = current.get(alias.variantId());
            if (now == null || !alias.productId().equals(now.productId())
                    || !alias.categoryId().equals(now.categoryId())
                    || !"ACTIVE".equals(now.productStatus()) || !"ACTIVE".equals(now.variantStatus())
                    || !"WOMEN".equals(now.demographicSegment())
                    || policy.slot(now.categoryId(), now.demographicSegment()).filter(alias.slot()::equals).isEmpty()
                    || !Objects.equals(alias.productName(), now.productName())
                    || !Objects.equals(alias.categoryName(), now.categoryName())
                    || !Objects.equals(alias.color(), now.color()) || !Objects.equals(alias.size(), now.size())
                    || !Objects.equals(alias.material(), now.material())) return null;
            if (!canVerifyHardColorExclusion(now, exclusions, policy)) return null;
            Item currentItem = GenerationEngineInputMapper.item(now, alias.slot(), policy);
            if (exclusions.stream().anyMatch(exclusion -> exclusion.hard() && exclusion.knownKindOrNull() != null
                    && exclusion.matches(currentItem))) return null;
            try { total = Math.addExact(total, now.unitPriceVnd()); }
            catch (ArithmeticException exception) { return null; }
            String reason = reasons.get(alias.alias());
            if (reason == null || reason.isBlank()) throw dependencyProtocol();
            items.add(new PersistedItem(alias.variantId(), alias.slot(), reason,
                    "TOP".equals(alias.slot()) ? 1 : 2));
        }
        if (items.size() != 2 || (context.hardBudget()
                && (context.maxBudgetVnd() == null || total > context.maxBudgetVnd()))) return null;
        items.sort(Comparator.comparingInt(PersistedItem::displayOrder));
        return List.copyOf(items);
    }

    private static String retrievalText(RequestContext context) {
        List<String> fields = new ArrayList<>();
        add(fields, context.message());
        add(fields, context.occasion());
        add(fields, context.intendedUse());
        add(fields, context.styleContext());
        add(fields, context.weatherContext());
        String query = String.join("\n", fields);
        if (query.isBlank() || query.getBytes(StandardCharsets.UTF_8).length > 6_000) {
            throw new StylistApiException(ErrorCode.INVALID_CONTEXT);
        }
        return query;
    }

    private static void add(List<String> fields, String value) {
        if (value != null && !value.isBlank()) fields.add(value.trim());
    }

    private GenerateRecommendationsResponse generateResponse(UUID requestId, String status,
            List<StoredRecommendation> stored, List<String> limitations) {
        return mapper.generationResponse(requestId, status, stored, limitations(status, limitations));
    }

    private static List<String> limitations(String status, List<String> requested) {
        if ("NO_RESULT".equals(status)) return List.of(NO_RESULT_BOUNDED);
        return List.copyOf(requested);
    }

    private RequestRead safeReadOwned(UUID customerId, UUID requestId) {
        try { return persistence.readOwned(customerId, requestId); }
        catch (DataAccessException exception) { throw new StylistApiException(ErrorCode.DEPENDENCY_UNAVAILABLE); }
    }

    private RequestRead readTerminalIfCommitted(UUID customerId, UUID requestId) {
        try {
            RequestRead read = persistence.readOwned(customerId, requestId);
            return isTerminal(read.context().status()) ? read : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static RuntimeException withRequestId(RuntimeException failure, UUID requestId) {
        if (failure instanceof StylistApiException apiException) {
            return new StylistApiException(apiException.errorCode(), requestId);
        }
        if (failure instanceof DataAccessException) {
            return new StylistApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, requestId);
        }
        return new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR, requestId);
    }

    private static boolean isTerminal(String status) {
        return "SUCCEEDED".equals(status) || "NO_RESULT".equals(status);
    }

    private static void ensureTime(long deadline) { ensureTime(deadline, Duration.ZERO); }

    private static void ensureTime(long deadline, Duration minimum) {
        if (System.nanoTime() >= deadline || Duration.ofNanos(deadline - System.nanoTime()).compareTo(minimum) < 0) {
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_TIMEOUT);
        }
    }

    private static Duration remaining(long deadline) {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_TIMEOUT);
        return Duration.ofNanos(nanos);
    }

    private <T> T runBeforeDeadline(Callable<T> task, long deadline) {
        return deadlineExecutor.run(task, deadline);
    }

    @PreDestroy
    void stopRemoteExecutor() {
        deadlineExecutor.close();
    }

    private static boolean canVerifyHardColorExclusion(HydratedVariant variant, List<Exclusion> exclusions,
                                                        Policy policy) {
        boolean hardColorExclusion = exclusions.stream().anyMatch(exclusion -> exclusion.hard()
                && exclusion.knownKindOrNull()
                        == EngineModel.Kind.EXCLUDE_COLOR);
        return !hardColorExclusion || policy.color(variant.color()).isPresent();
    }

    private static StylistApiException invalidContext() { return new StylistApiException(ErrorCode.INVALID_CONTEXT); }
    private static StylistApiException dependencyProtocol() {
        return new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
    }

    private record ExecutionResult(String status, List<PersistedRecommendation> recommendations,
                                   List<String> limitations) {
        private ExecutionResult {
            recommendations = List.copyOf(recommendations);
            limitations = List.copyOf(limitations);
        }
    }

    private record CandidateSet(List<Item> engineItems, Map<UUID, HydratedVariant> currentByVariant) { }

}
