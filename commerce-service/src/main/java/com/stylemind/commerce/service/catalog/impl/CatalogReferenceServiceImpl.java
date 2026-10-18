package com.stylemind.commerce.service.catalog.impl;

import com.stylemind.commerce.dto.catalog.CatalogReferenceListResponse;
import com.stylemind.commerce.dto.catalog.CatalogReferenceResponse;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.model.catalog.CatalogReference;
import com.stylemind.commerce.model.catalog.CatalogReferenceCommand;
import com.stylemind.commerce.model.catalog.CatalogReferenceType;
import com.stylemind.commerce.repository.catalog.CatalogReferenceRepository;
import com.stylemind.commerce.service.catalog.CatalogReferenceService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CatalogReferenceServiceImpl implements CatalogReferenceService {

    private final CatalogReferenceRepository repository;

    @Transactional(readOnly = true)
    public CatalogReferenceListResponse list(CatalogReferenceType type) {
        return new CatalogReferenceListResponse(repository.list(type).stream().map(this::response).toList());
    }

    @Transactional(readOnly = true)
    public CatalogReferenceResponse get(CatalogReferenceType type, UUID id) {
        return repository.find(type, id).map(this::response).orElseThrow(this::notFound);
    }

    @Transactional
    public CatalogReferenceResponse create(CatalogReferenceType type, CatalogReferenceCommand command) {
        try {
            return response(repository.insert(type, UUID.randomUUID(), command));
        } catch (DuplicateKeyException exception) {
            throw duplicateSlug();
        }
    }

    @Transactional
    public CatalogReferenceResponse replace(CatalogReferenceType type, UUID id, CatalogReferenceCommand command) {
        try {
            return repository.update(type, id, command).map(this::response).orElseThrow(this::notFound);
        } catch (DuplicateKeyException exception) {
            throw duplicateSlug();
        }
    }

    @Transactional
    public void delete(CatalogReferenceType type, UUID id) {
        if (!repository.exists(type, id)) {
            throw notFound();
        }
        if (repository.isReferencedByProduct(type, id)) {
            throw new ApiOperationException(HttpStatus.CONFLICT, "CATALOG_REFERENCE_IN_USE",
                    "Catalog reference is used by one or more products");
        }
        if (repository.delete(type, id) != 1) {
            throw new IllegalStateException("Catalog reference delete did not affect one row");
        }
    }

    private CatalogReferenceResponse response(CatalogReference reference) {
        return new CatalogReferenceResponse(reference.id(), reference.name(), reference.slug(), reference.active());
    }

    private ApiOperationException notFound() {
        return new ApiOperationException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found");
    }

    private ApiOperationException duplicateSlug() {
        return new ApiOperationException(HttpStatus.CONFLICT, "DUPLICATE_SLUG",
                "A catalog reference with this slug already exists");
    }
}
