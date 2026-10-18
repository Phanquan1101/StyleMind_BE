package com.stylemind.commerce.service.catalog;

import com.stylemind.commerce.dto.catalog.CatalogReferenceListResponse;
import com.stylemind.commerce.dto.catalog.CatalogReferenceResponse;
import com.stylemind.commerce.dto.catalog.CatalogReferenceRequest;
import com.stylemind.commerce.enums.CatalogReferenceType;
import java.util.UUID;

public interface CatalogReferenceService {

    CatalogReferenceListResponse list(CatalogReferenceType type);

    CatalogReferenceResponse get(CatalogReferenceType type, UUID id);

    CatalogReferenceResponse create(CatalogReferenceType type, CatalogReferenceRequest command);

    CatalogReferenceResponse replace(CatalogReferenceType type, UUID id, CatalogReferenceRequest command);

    void delete(CatalogReferenceType type, UUID id);
}
