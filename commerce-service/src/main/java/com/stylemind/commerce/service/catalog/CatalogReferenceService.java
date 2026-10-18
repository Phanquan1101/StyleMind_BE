package com.stylemind.commerce.service.catalog;

import com.stylemind.commerce.dto.catalog.CatalogReferenceListResponse;
import com.stylemind.commerce.dto.catalog.CatalogReferenceResponse;
import com.stylemind.commerce.model.catalog.CatalogReferenceCommand;
import com.stylemind.commerce.model.catalog.CatalogReferenceType;
import java.util.UUID;

public interface CatalogReferenceService {

    CatalogReferenceListResponse list(CatalogReferenceType type);

    CatalogReferenceResponse get(CatalogReferenceType type, UUID id);

    CatalogReferenceResponse create(CatalogReferenceType type, CatalogReferenceCommand command);

    CatalogReferenceResponse replace(CatalogReferenceType type, UUID id, CatalogReferenceCommand command);

    void delete(CatalogReferenceType type, UUID id);
}
