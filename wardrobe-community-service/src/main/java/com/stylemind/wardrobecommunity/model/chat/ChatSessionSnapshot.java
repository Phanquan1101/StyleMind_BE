package com.stylemind.wardrobecommunity.model.chat;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Safe local projection; no message contents, participant rows or image storage references. */
public record ChatSessionSnapshot(
        UUID chatSessionId, UUID friendAccountId, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}

