package com.stylemind.stylist.mapper.request;

import com.stylemind.stylist.dto.request.CreateStylingRequestRequest;
import com.stylemind.stylist.dto.request.CreateStylingRequestRequest.ConstraintInput;
import com.stylemind.stylist.dto.request.CreateStylingRequestResponse;
import com.stylemind.stylist.dto.request.StylingRequestResponse;
import com.stylemind.stylist.dto.request.StylingRequestResponse.ConstraintResponse;
import com.stylemind.stylist.entity.constraint.StylingConstraintEntity;
import com.stylemind.stylist.entity.message.StylistMessageEntity;
import com.stylemind.stylist.entity.request.StylingRequestEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class StylingRequestMapper {

    public StylistMessageEntity newUserMessage(UUID sessionId, String content, Instant sentAt) {
        return new StylistMessageEntity(UUID.randomUUID(), sessionId, "USER", content, sentAt);
    }

    public StylingRequestEntity newRequest(UUID sessionId, UUID sourceMessageId,
                                          CreateStylingRequestRequest request, Instant requestedAt) {
        return new StylingRequestEntity(UUID.randomUUID(), sessionId, sourceMessageId,
                request.occasion(), request.intendedUse(), request.styleContext(), request.generalWeatherContext(),
                request.maxBudgetVnd(), request.budgetIsHard(), "RECEIVED", requestedAt);
    }

    public List<StylingConstraintEntity> newConstraints(UUID requestId, List<ConstraintInput> constraints) {
        return constraints.stream().map(constraint -> new StylingConstraintEntity(UUID.randomUUID(), requestId,
                constraint.constraintKind().name(), constraint.catalogEntityId(), constraint.textValue(),
                constraint.hard())).toList();
    }

    public CreateStylingRequestResponse created(StylingRequestEntity request) {
        return new CreateStylingRequestResponse(request.getStylingRequestId(), request.getStylistSessionId(),
                request.getSourceMessageId(), request.getRequestStatus(), request.getRequestedAt(), false);
    }

    public StylingRequestResponse response(StylingRequestEntity request, List<StylingConstraintEntity> constraints) {
        return new StylingRequestResponse(request.getStylingRequestId(), request.getStylistSessionId(),
                request.getSourceMessageId(), request.getRequestStatus(), request.getRequestedAt(),
                !"RECEIVED".equals(request.getRequestStatus()),
                request.getOccasion(), request.getIntendedUse(), request.getStyleContext(),
                request.getGeneralWeatherContext(), request.getMaxBudgetVnd(), request.isBudgetIsHard(),
                constraints.stream().map(this::constraintResponse).toList());
    }

    private ConstraintResponse constraintResponse(StylingConstraintEntity constraint) {
        return new ConstraintResponse(constraint.getConstraintKind(), constraint.getCatalogEntityId(),
                constraint.getTextValue(), constraint.isHard());
    }
}
