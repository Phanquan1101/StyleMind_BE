package com.stylemind.commerce.dto.catalog;

import java.util.List;

public record CatalogReferenceListResponse(
        List<CatalogReferenceResponse> items) {
}
