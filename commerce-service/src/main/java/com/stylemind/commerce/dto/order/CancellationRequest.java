package com.stylemind.commerce.dto.order;

import com.stylemind.commerce.enums.CancellationStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CancellationRequest(
        UUID cancellationRequestId,
        UUID orderId,
        UUID requestedByUserId,
        String reason,
        CancellationStatus requestStatus,
        OffsetDateTime requestedAt,
        UUID reviewedByUserId,
        OffsetDateTime reviewedAt,
        String decisionReason) {
}
