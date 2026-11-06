package com.stylemind.wardrobecommunity.model.outfit;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record FriendOutfitPageSnapshot(List<Entry> items, long total) {
    public record Entry(
            UUID outfitId,
            String name,
            long itemCount,
            String visibility,
            OffsetDateTime updatedAt) {}
}
