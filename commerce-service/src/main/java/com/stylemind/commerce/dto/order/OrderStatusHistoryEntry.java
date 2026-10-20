package com.stylemind.commerce.dto.order;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderStatusHistoryEntry(
        UUID historyId,
        String fromStatus,
        String toStatus,
        UUID actorUserId,
        OffsetDateTime changedAt,
        String reason) {
}
