package com.stylemind.stylist.service.impl.generation;

import com.stylemind.stylist.entity.constraint.StylingConstraintEntity;
import com.stylemind.stylist.entity.message.StylistMessageEntity;
import com.stylemind.stylist.entity.recommendation.RecommendationItemEntity;
import com.stylemind.stylist.entity.recommendation.WardrobeRecommendationEntity;
import com.stylemind.stylist.entity.request.StylingRequestEntity;
import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import com.stylemind.stylist.mapper.recommendation.RecommendationMapper;
import com.stylemind.stylist.model.generation.GenerationModel.Claim;
import com.stylemind.stylist.model.generation.GenerationModel.Constraint;
import com.stylemind.stylist.model.generation.GenerationModel.PersistedRecommendation;
import com.stylemind.stylist.model.generation.GenerationModel.RequestContext;
import com.stylemind.stylist.model.generation.GenerationModel.RequestRead;
import com.stylemind.stylist.model.generation.GenerationModel.StoredRecommendation;
import com.stylemind.stylist.repository.constraint.StylingConstraintRepository;
import com.stylemind.stylist.repository.message.StylistMessageRepository;
import com.stylemind.stylist.repository.recommendation.RecommendationItemRepository;
import com.stylemind.stylist.repository.recommendation.WardrobeRecommendationRepository;
import com.stylemind.stylist.repository.request.StylingRequestRepository;
import com.stylemind.stylist.repository.session.StylistSessionRepository;
import com.stylemind.stylist.service.generation.GenerationPersistenceService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
public class GenerationPersistenceServiceImpl implements GenerationPersistenceService {
    private final StylingRequestRepository requests;
    private final StylistSessionRepository sessions;
    private final StylistMessageRepository messages;
    private final StylingConstraintRepository constraints;
    private final WardrobeRecommendationRepository recommendations;
    private final RecommendationItemRepository items;
    private final RecommendationMapper mapper;

    public GenerationPersistenceServiceImpl(StylingRequestRepository requests, StylistSessionRepository sessions,
            StylistMessageRepository messages, StylingConstraintRepository constraints,
            WardrobeRecommendationRepository recommendations, RecommendationItemRepository items,
            RecommendationMapper mapper) {
        this.requests = requests;
        this.sessions = sessions;
        this.messages = messages;
        this.constraints = constraints;
        this.recommendations = recommendations;
        this.items = items;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public RequestRead readOwned(UUID customerId, UUID requestId) {
        StylingRequestEntity request = owned(customerId, requestId);
        RequestContext context = context(request, customerId);
        List<WardrobeRecommendationEntity> recommendationRows = recommendations
                .findAllByStylingRequestIdOrderByGeneratedAtAscRecommendationIdAsc(requestId);
        return new RequestRead(context, stored(recommendationRows));
    }

    @Override
    @Transactional(readOnly = true)
    public StoredRecommendation readOwnedRecommendation(UUID customerId, UUID recommendationId) {
        WardrobeRecommendationEntity recommendation = recommendations
                .findByRecommendationIdAndOwner(recommendationId, customerId)
                .orElseThrow(() -> new StylistApiException(ErrorCode.RESOURCE_NOT_FOUND));
        return mapper.stored(recommendation,
                items.findAllByRecommendationIdOrderByDisplayOrderAsc(recommendationId));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Claim claim(UUID customerId, UUID requestId) {
        UUID sessionId = ownedSessionId(customerId, requestId);
        var session = sessions.findOwnedForUpdate(sessionId, customerId)
                .orElseThrow(() -> new StylistApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (session.getClosedAt() != null) throw new StylistApiException(ErrorCode.SESSION_CLOSED);
        StylingRequestEntity request = requests.findOwnedForUpdate(requestId, customerId)
                .orElseThrow(() -> new StylistApiException(ErrorCode.RESOURCE_NOT_FOUND));
        String status = request.getRequestStatus();
        if ("PROCESSING".equals(status) || "FAILED".equals(status)
                || "SUCCEEDED".equals(status) || "NO_RESULT".equals(status)) {
            return new Claim(status, false);
        }
        if (!"RECEIVED".equals(status)) throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
        request.setRequestStatus("PROCESSING");
        requests.save(request);
        return new Claim("PROCESSING", true);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<StoredRecommendation> complete(UUID customerId, UUID requestId, String status,
                                               List<PersistedRecommendation> generated) {
        UUID sessionId = ownedSessionId(customerId, requestId);
        sessions.findOwnedForUpdate(sessionId, customerId)
                .orElseThrow(() -> new StylistApiException(ErrorCode.RESOURCE_NOT_FOUND));
        StylingRequestEntity request = requests.findOwnedForUpdate(requestId, customerId)
                .orElseThrow(() -> new StylistApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!"PROCESSING".equals(request.getRequestStatus())) {
            throw new StylistApiException(ErrorCode.GENERATION_ALREADY_ATTEMPTED, requestId);
        }
        if ("SUCCEEDED".equals(status)) {
            if (generated == null || generated.isEmpty() || generated.size() > 3) throw invalidFinalization();
            for (PersistedRecommendation recommendation : generated) {
                validateRecommendation(recommendation);
                recommendations.save(mapper.recommendation(requestId, recommendation));
                items.saveAll(mapper.items(recommendation.recommendationId(), recommendation.items()));
            }
        } else if (!("NO_RESULT".equals(status) && (generated == null || generated.isEmpty()))) {
            throw invalidFinalization();
        }
        request.setRequestStatus(status);
        requests.save(request);
        // Force all aggregate constraints in this transaction; any failure rolls back rows and terminal status.
        recommendations.flush();
        items.flush();
        requests.flush();
        return stored(recommendations.findAllByStylingRequestIdOrderByGeneratedAtAscRecommendationIdAsc(requestId));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID customerId, UUID requestId) {
        UUID sessionId = requests.findOwnedSessionId(requestId, customerId).orElse(null);
        if (sessionId == null) return;
        sessions.findOwnedForUpdate(sessionId, customerId)
                .orElseThrow(() -> new StylistApiException(ErrorCode.RESOURCE_NOT_FOUND));
        StylingRequestEntity request = requests.findOwnedForUpdate(requestId, customerId).orElse(null);
        if (request != null && "PROCESSING".equals(request.getRequestStatus())) {
            request.setRequestStatus("FAILED");
            requests.save(request);
        }
    }

    private StylingRequestEntity owned(UUID customerId, UUID requestId) {
        return requests.findByRequestIdAndOwner(requestId, customerId)
                .orElseThrow(() -> new StylistApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private RequestContext context(StylingRequestEntity request, UUID customerId) {
        var session = sessions.findByStylistSessionIdAndCustomerId(request.getStylistSessionId(),
                customerId);
        return context(request, session.map(row -> row.getClosedAt() != null).orElse(true));
    }

    private UUID ownedSessionId(UUID customerId, UUID requestId) {
        return requests.findOwnedSessionId(requestId, customerId)
                .orElseThrow(() -> new StylistApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private RequestContext context(StylingRequestEntity request, boolean sessionClosed) {
        String message = null;
        if (request.getSourceMessageId() != null) {
            StylistMessageEntity row = messages.findByStylistMessageIdAndStylistSessionIdAndSpeakerRole(
                            request.getSourceMessageId(), request.getStylistSessionId(), "USER")
                    .orElseThrow(() -> new StylistApiException(ErrorCode.RESOURCE_NOT_FOUND));
            message = row.getContent();
        }
        List<Constraint> requestConstraints = constraints
                .findAllByStylingRequestIdOrderByConstraintId(request.getStylingRequestId()).stream()
                .map(this::constraint).toList();
        return new RequestContext(request.getStylingRequestId(), request.getStylistSessionId(),
                request.getRequestStatus(), message, request.getOccasion(), request.getIntendedUse(),
                request.getStyleContext(), request.getGeneralWeatherContext(), request.getMaxBudgetVnd(),
                request.isBudgetIsHard(), sessionClosed, requestConstraints);
    }

    private Constraint constraint(StylingConstraintEntity row) {
        return new Constraint(row.getConstraintKind(), row.getCatalogEntityId(), row.getTextValue(), row.isHard());
    }

    private List<StoredRecommendation> stored(List<WardrobeRecommendationEntity> parentRows) {
        if (parentRows.isEmpty()) return List.of();
        List<UUID> ids = parentRows.stream().map(WardrobeRecommendationEntity::getRecommendationId).toList();
        Map<UUID, List<RecommendationItemEntity>> itemsByRecommendation = new HashMap<>();
        items.findAllByRecommendationIdInOrderByRecommendationIdAscDisplayOrderAsc(ids).forEach(item ->
                itemsByRecommendation.computeIfAbsent(item.getRecommendationId(), ignored -> new ArrayList<>()).add(item));
        return parentRows.stream().map(parent -> mapper.stored(parent,
                itemsByRecommendation.getOrDefault(parent.getRecommendationId(), List.of()))).toList();
    }

    private static void validateRecommendation(PersistedRecommendation recommendation) {
        if (recommendation == null || recommendation.recommendationId() == null
                || recommendation.explanation() == null || recommendation.explanation().isBlank()
                || recommendation.explanation().codePointCount(0, recommendation.explanation().length()) > 2_000
                || recommendation.stylingTips() != null
                && recommendation.stylingTips().codePointCount(0, recommendation.stylingTips().length()) > 1_000
                || recommendation.items() == null || recommendation.items().size() != 2) throw invalidFinalization();
            var sorted = recommendation.items().stream().sorted(Comparator.comparingInt(i -> i.displayOrder())).toList();
        if (sorted.get(0).displayOrder() != 1 || !"TOP".equals(sorted.get(0).slot())
                || sorted.get(1).displayOrder() != 2 || !"BOTTOM".equals(sorted.get(1).slot())
                || sorted.stream().anyMatch(item -> item.shopVariantId() == null || item.reason() == null
                        || item.reason().isBlank() || item.reason().codePointCount(0, item.reason().length()) > 500)
                || sorted.stream().map(item -> item.shopVariantId()).distinct().count() != 2) {
            throw invalidFinalization();
        }
    }

    private static StylistApiException invalidFinalization() {
        return new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
    }
}
