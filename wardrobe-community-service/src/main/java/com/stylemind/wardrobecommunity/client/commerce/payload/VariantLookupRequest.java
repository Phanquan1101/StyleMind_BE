package com.stylemind.wardrobecommunity.client.commerce.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

/** Private payload of Commerce's internal variant lookup; not an API DTO of this service. */
public record VariantLookupRequest(
        @JsonProperty("variant_ids") List<UUID> variantIds) {
}
