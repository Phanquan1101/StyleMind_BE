package com.stylemind.stylist.service.impl.recommendation;

import com.stylemind.stylist.client.commerce.CommerceCatalogClient;
import com.stylemind.stylist.client.commerce.CommerceCatalogClient.HydratedVariant;
import com.stylemind.stylist.dto.recommendation.InternalRecommendationResult;
import com.stylemind.stylist.dto.recommendation.InternalRecommendationResult.RecommendedItem;
import com.stylemind.stylist.dto.recommendation.InternalRecommendationResult.RecommendedOutfit;
import com.stylemind.stylist.entity.constraint.StylingConstraintEntity;
import com.stylemind.stylist.entity.message.StylistMessageEntity;
import com.stylemind.stylist.entity.request.StylingRequestEntity;
import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import com.stylemind.stylist.model.recommendation.EngineModel;
import com.stylemind.stylist.model.recommendation.EngineModel.Evidence;
import com.stylemind.stylist.model.recommendation.EngineModel.Exclusion;
import com.stylemind.stylist.model.recommendation.EngineModel.Holding;
import com.stylemind.stylist.model.recommendation.EngineModel.Input;
import com.stylemind.stylist.model.recommendation.EngineModel.Item;
import com.stylemind.stylist.model.recommendation.EngineModel.Outfit;
import com.stylemind.stylist.model.recommendation.EngineModel.Preferences;
import com.stylemind.stylist.model.recommendation.EngineModel.Result;
import com.stylemind.stylist.model.recommendation.EngineModel.ShopVariantReference;
import com.stylemind.stylist.model.recommendation.EngineModel.Slot;
import com.stylemind.stylist.model.recommendation.EngineModel.Vocabulary;
import com.stylemind.stylist.model.retrieval.RetrievalModel;
import com.stylemind.stylist.model.retrieval.RetrievalModel.CandidateSearchQuery;
import com.stylemind.stylist.model.retrieval.RetrievalModel.CandidateSearchResult;
import com.stylemind.stylist.model.retrieval.RetrievalModel.CatalogCandidate;
import com.stylemind.stylist.model.retrieval.RetrievalModel.RetrievedCandidate;
import com.stylemind.stylist.repository.constraint.StylingConstraintRepository;
import com.stylemind.stylist.repository.message.StylistMessageRepository;
import com.stylemind.stylist.repository.request.StylingRequestRepository;
import com.stylemind.stylist.service.recommendation.internal.BudgetPolicy;
import com.stylemind.stylist.service.recommendation.internal.OutfitEngine;
import com.stylemind.stylist.service.retrieval.CandidateRetrievalService;
import com.stylemind.stylist.service.recommendation.StylingRecommendationService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Bounded internal fixture pipeline. Remote calls occur only after the owner-scoped read transaction exits. */
public class StylingRecommendationServiceImpl implements StylingRecommendationService {
    private static final int RETRIEVAL_LIMIT_PER_SLOT = RetrievalModel.MAX_LIMIT;
    private static final String TOP_CATEGORY_ID = "fbb973c7-c888-53ba-8648-76a4700c3757";
    private static final String BOTTOM_CATEGORY_ID = "397b5054-84f4-5cc6-8047-56787826b9bf";

    private final StylingRequestRepository requestRepository;
    private final StylingConstraintRepository constraintRepository;
    private final StylistMessageRepository messageRepository;
    private final ObjectProvider<CandidateRetrievalService> retrievalProvider;
    private final CommerceCatalogClient commerce;
    private final TransactionTemplate requestReadTransaction;
    private final OutfitEngine engine = new OutfitEngine();

    public StylingRecommendationServiceImpl(
            StylingRequestRepository requestRepository,
            StylingConstraintRepository constraintRepository,
            StylistMessageRepository messageRepository,
            ObjectProvider<CandidateRetrievalService> retrievalProvider,
            CommerceCatalogClient commerce,
            PlatformTransactionManager transactionManager) {
        this.requestRepository = requestRepository;
        this.constraintRepository = constraintRepository;
        this.messageRepository = messageRepository;
        this.retrievalProvider = retrievalProvider;
        this.commerce = commerce;
        this.requestReadTransaction = new TransactionTemplate(transactionManager);
        this.requestReadTransaction.setReadOnly(true);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public InternalRecommendationResult recommend(UUID trustedCustomerId, UUID requestId) {
        if (trustedCustomerId == null || requestId == null) throw new StylistApiException(ErrorCode.INVALID_REQUEST);
        RequestContext context = requestReadTransaction.execute(status -> readOwnedContext(trustedCustomerId, requestId));
        if (context == null) throw new StylistApiException(ErrorCode.DEPENDENCY_PROTOCOL_ERROR);

        CandidateRetrievalService retrieval = retrievalProvider.getIfAvailable();
        if (retrieval == null) throw new StylistApiException(ErrorCode.DEPENDENCY_UNAVAILABLE);

        String query = context.searchText();
        CandidateSearchResult topHits = retrieval.search(new CandidateSearchQuery(query, "TOP", RETRIEVAL_LIMIT_PER_SLOT));
        CandidateSearchResult bottomHits = retrieval.search(new CandidateSearchQuery(query, "BOTTOM", RETRIEVAL_LIMIT_PER_SLOT));
        List<CatalogCandidate> retrieved = validateRetrievedCandidates(topHits, bottomHits);

        Map<UUID, UUID> expectedProductIds = new LinkedHashMap<>();
        retrieved.forEach(candidate -> expectedProductIds.put(candidate.variantId(), candidate.productId()));
        Map<UUID, HydratedVariant> initialCommerce = commerce.hydrate(expectedProductIds);
        List<CandidateBinding> bindings = bindCurrentCandidates(retrieved, initialCommerce);

        List<Exclusion> exclusions = context.constraints().stream().map(StylingRecommendationServiceImpl::exclusion).toList();
        Set<String> currentColors = bindings.stream().map(binding -> binding.item().color())
                .filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
        Vocabulary vocabulary = new Vocabulary(Set.of(), currentColors, Set.of());
        Input engineInput = new Input(bindings.stream().map(CandidateBinding::item).toList(), List.of(),
                context.maxBudgetVnd(), context.budgetIsHard(), exclusions,
                new Preferences(Set.of(), Set.of(), Set.of()), vocabulary, new Evidence(Set.of(), Set.of()));

        Result engineResult;
        try {
            engineResult = engine.recommend(engineInput);
        } catch (IllegalArgumentException exception) {
            throw new StylistApiException(ErrorCode.INVALID_CONTEXT);
        }

        List<Outfit> selected = engineResult.outfits();
        if (selected.isEmpty()) {
            return new InternalRecommendationResult(requestId, List.of(), engineResult.warnings(),
                    engineResult.candidatesUsed(), engineResult.combinationsChecked());
        }

        Map<UUID, UUID> selectedProducts = new LinkedHashMap<>();
        Map<UUID, CatalogCandidate> fixtureByVariant = new HashMap<>();
        bindings.forEach(binding -> {
            fixtureByVariant.put(binding.fixture().variantId(), binding.fixture());
        });
        for (Outfit outfit : selected) {
            for (Item item : outfit.items()) {
                selectedProducts.put(item.variantId(), item.productId());
            }
        }

        Map<UUID, HydratedVariant> finalCommerce = commerce.hydrate(selectedProducts);
        List<RecommendedOutfit> finalOutfits = new ArrayList<>();
        for (Outfit outfit : selected) {
            List<Item> currentItems = new ArrayList<>(outfit.items().size());
            List<HydratedVariant> currentViews = new ArrayList<>(outfit.items().size());
            boolean stillEligible = true;
            for (Item selectedItem : outfit.items()) {
                CatalogCandidate fixture = fixtureByVariant.get(selectedItem.variantId());
                HydratedVariant current = finalCommerce.get(selectedItem.variantId());
                if (fixture == null || current == null || !isCurrentFixtureMatch(fixture, current)) {
                    stillEligible = false;
                    break;
                }
                Item refreshed = engineItem(fixture, current);
                if (exclusions.stream().anyMatch(exclusion -> exclusion.hard() && exclusion.matches(refreshed))) {
                    stillEligible = false;
                    break;
                }
                currentItems.add(refreshed);
                currentViews.add(current);
            }
            if (!stillEligible || currentItems.size() != 2) continue;

            long currentCost;
            try {
                currentCost = BudgetPolicy.total(currentItems);
            } catch (ArithmeticException exception) {
                continue;
            }
            if (context.budgetIsHard() && currentCost > context.maxBudgetVnd()) continue;

            List<RecommendedItem> outputItems = new ArrayList<>(currentViews.size());
            for (int i = 0; i < currentViews.size(); i++) outputItems.add(toRecommendedItem(currentItems.get(i), currentViews.get(i)));
            finalOutfits.add(new RecommendedOutfit(currentCost, outputItems));
        }

        return new InternalRecommendationResult(requestId, finalOutfits, engineResult.warnings(),
                engineResult.candidatesUsed(), engineResult.combinationsChecked());
    }

    private RequestContext readOwnedContext(UUID customerId, UUID requestId) {
        StylingRequestEntity request = requestRepository.findByRequestIdAndOwner(requestId, customerId)
                .orElseThrow(() -> new StylistApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!"RECEIVED".equals(request.getRequestStatus())) {
            throw new StylistApiException(ErrorCode.DEPENDENCY_PROTOCOL_ERROR);
        }
        String sourceMessage = null;
        UUID sourceMessageId = request.getSourceMessageId();
        if (sourceMessageId != null) {
            StylistMessageEntity message = messageRepository.findByStylistMessageIdAndStylistSessionIdAndSpeakerRole(
                            sourceMessageId, request.getStylistSessionId(), "USER")
                    .orElseThrow(() -> new StylistApiException(ErrorCode.RESOURCE_NOT_FOUND));
            sourceMessage = message.getContent();
        }
        List<ConstraintInput> constraints = constraintRepository
                .findAllByStylingRequestIdOrderByConstraintId(requestId).stream()
                .map(ConstraintInput::from).toList();
        return new RequestContext(sourceMessage, request.getOccasion(), request.getIntendedUse(),
                request.getStyleContext(), request.getGeneralWeatherContext(), request.getMaxBudgetVnd(),
                request.isBudgetIsHard(), constraints);
    }

    private static List<CatalogCandidate> validateRetrievedCandidates(CandidateSearchResult topHits,
                                                                       CandidateSearchResult bottomHits) {
        if (topHits == null || bottomHits == null) throw new StylistApiException(ErrorCode.DEPENDENCY_PROTOCOL_ERROR);
        Map<UUID, CatalogCandidate> unique = new LinkedHashMap<>();
        addHits(unique, topHits, "TOP");
        addHits(unique, bottomHits, "BOTTOM");
        return List.copyOf(unique.values());
    }

    private static void addHits(Map<UUID, CatalogCandidate> unique, CandidateSearchResult result, String requestedSlot) {
        for (RetrievedCandidate hit : result.candidates()) {
            CatalogCandidate candidate = hit.candidate();
            if (!isApprovedFixture(candidate, requestedSlot) || unique.putIfAbsent(candidate.variantId(), candidate) != null) {
                throw new StylistApiException(ErrorCode.DEPENDENCY_PROTOCOL_ERROR);
            }
        }
    }

    private static List<CandidateBinding> bindCurrentCandidates(List<CatalogCandidate> candidates,
                                                                  Map<UUID, HydratedVariant> currentCatalog) {
        List<CandidateBinding> bindings = new ArrayList<>();
        for (CatalogCandidate candidate : candidates) {
            HydratedVariant current = currentCatalog.get(candidate.variantId());
            if (current == null || !isCurrentFixtureMatch(candidate, current)) continue;
            bindings.add(new CandidateBinding(candidate, engineItem(candidate, current)));
        }
        return List.copyOf(bindings);
    }

    private static boolean isApprovedFixture(CatalogCandidate candidate, String requestedSlot) {
        if (candidate == null || candidate.productId() == null || candidate.variantId() == null
                || candidate.categoryId() == null || !requestedSlot.equals(candidate.slot())
                || !"WOMEN".equals(candidate.demographicSegmentRaw()) || !candidate.reviewSignals().isEmpty()
                || !"APPROVED_TEST_ONLY".equals(candidate.categoryMappingStatus())
                || !"APPROVED_TEST_ONLY".equals(candidate.fixtureEligibilityStatus())
                || !RetrievalModel.FIXTURE_TAG.equals(candidate.fixtureTag())
                || !RetrievalModel.SOURCE_SEED.equals(candidate.source())
                || !RetrievalModel.SOURCE_SEED_FILE.equals(candidate.sourceFile())
                || !RetrievalModel.SOURCE_SEED_SHA256.equals(candidate.sourceSha256())
                || !RetrievalModel.SOURCE_RUNTIME_STATUS.equals(candidate.sourceRuntimeStatus())
                || !RetrievalModel.PRICE_SEMANTICS.equals(candidate.priceSemantics())
                || !"NOT_IN_SEED".equals(candidate.productStatusSource())
                || !"NOT_IN_SEED".equals(candidate.variantStatusSource())) return false;
        return isApprovedCategory(candidate.categoryId(), candidate.categorySlug(), requestedSlot);
    }

    private static boolean isCurrentFixtureMatch(CatalogCandidate fixture, HydratedVariant current) {
        return current.productId().equals(fixture.productId())
                && current.categoryId().equals(fixture.categoryId())
                && "ACTIVE".equals(current.productStatus())
                && "ACTIVE".equals(current.variantStatus())
                && "WOMEN".equals(current.demographicSegment())
                && isApprovedCategory(current.categoryId(), fixture.categorySlug(), fixture.slot());
    }

    private static boolean isApprovedCategory(UUID categoryId, String slug, String slot) {
        return "TOP".equals(slot) && TOP_CATEGORY_ID.equals(categoryId.toString()) && "ao".equals(slug)
                || "BOTTOM".equals(slot) && BOTTOM_CATEGORY_ID.equals(categoryId.toString()) && "quan".equals(slug);
    }

    private static Item engineItem(CatalogCandidate fixture, HydratedVariant current) {
        return new Item(current.productId(), current.categoryId(), current.brandId(),
                "TOP".equals(fixture.slot()) ? Slot.TOP : Slot.BOTTOM,
                current.color(), Set.of(), current.unitPriceVnd(), true, true,
                Holding.NONE, new ShopVariantReference(current.variantId()), false);
    }

    private static Exclusion exclusion(ConstraintInput input) {
        return new Exclusion(input.kind(), input.catalogEntityId(), input.textValue(), input.hard());
    }

    private static RecommendedItem toRecommendedItem(Item item, HydratedVariant view) {
        return new RecommendedItem(item.productId(), item.variantId(), item.categoryId(), item.brandId(),
                item.slot().name(), view.productName(), view.categoryName(), view.brandName(),
                view.color(), view.size(), view.material(), view.unitPriceVnd(), view.images());
    }

    private record RequestContext(String sourceMessage, String occasion, String intendedUse,
                                  String styleContext, String weatherContext, Long maxBudgetVnd,
                                  boolean budgetIsHard, List<ConstraintInput> constraints) {
        private RequestContext { constraints = List.copyOf(constraints); }

        private String searchText() {
            List<String> parts = new ArrayList<>();
            addText(parts, sourceMessage);
            addText(parts, occasion);
            addText(parts, intendedUse);
            addText(parts, styleContext);
            addText(parts, weatherContext);
            if (parts.isEmpty()) throw new StylistApiException(ErrorCode.INVALID_CONTEXT);
            return String.join("\n", parts);
        }

        private static void addText(List<String> parts, String value) {
            if (value != null && !value.isBlank()) parts.add(value);
        }
    }

    private record ConstraintInput(String kind, UUID catalogEntityId, String textValue, boolean hard) {
        private static ConstraintInput from(StylingConstraintEntity entity) {
            return new ConstraintInput(entity.getConstraintKind(), entity.getCatalogEntityId(),
                    entity.getTextValue(), entity.isHard());
        }
    }

    private record CandidateBinding(CatalogCandidate fixture, Item item) { }
}
