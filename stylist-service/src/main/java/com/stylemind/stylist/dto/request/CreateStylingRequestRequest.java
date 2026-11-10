package com.stylemind.stylist.dto.request;

import java.util.List;
import java.util.UUID;

public record CreateStylingRequestRequest(
        String message,
        UUID sourceMessageId,
        String occasion,
        String intendedUse,
        String styleContext,
        String generalWeatherContext,
        Long maxBudgetVnd,
        boolean budgetIsHard,
        List<ConstraintInput> constraints) {

    public record ConstraintInput(
            StylingConstraintKind constraintKind,
            UUID catalogEntityId,
            String textValue,
            boolean hard) {
    }
}
