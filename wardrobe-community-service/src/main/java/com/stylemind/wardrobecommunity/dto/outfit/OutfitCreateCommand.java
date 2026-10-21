package com.stylemind.wardrobecommunity.dto.outfit;

import java.util.List;
import java.util.UUID;

public record OutfitCreateCommand(String name, String creationSource, List<UUID> variantIds) {
}
