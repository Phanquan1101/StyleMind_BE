package com.stylemind.wardrobecommunity.model.outfit;

import java.util.List;
import java.util.UUID;

public record OutfitViewerSnapshot(UUID outfitId, UUID ownerId, String name, List<Item> items) {
    public record Item(UUID itemId, UUID variantId, int order) {}
}
