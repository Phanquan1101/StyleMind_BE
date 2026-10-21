package com.stylemind.wardrobecommunity.client.commerce.payload;

import java.util.List;

public record VariantLookupResponse(
        List<VariantLookupItem> items) {
}
