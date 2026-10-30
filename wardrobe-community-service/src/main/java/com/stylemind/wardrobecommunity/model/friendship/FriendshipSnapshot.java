package com.stylemind.wardrobecommunity.model.friendship;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Local immutable row data passed from the persistence transaction to service orchestration. */
public record FriendshipSnapshot(UUID friendshipId, UUID peerId, OffsetDateTime establishedAt) {
}
