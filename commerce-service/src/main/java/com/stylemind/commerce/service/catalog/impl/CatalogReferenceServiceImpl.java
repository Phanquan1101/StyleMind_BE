package com.stylemind.commerce.service.catalog.impl;

import com.stylemind.commerce.dto.catalog.CatalogReferenceListResponse;
import com.stylemind.commerce.dto.catalog.CatalogReferenceRequest;
import com.stylemind.commerce.dto.catalog.CatalogReferenceResponse;
import com.stylemind.commerce.entity.Brand;
import com.stylemind.commerce.entity.Category;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.enums.CatalogReferenceType;
import com.stylemind.commerce.repository.catalog.BrandRepository;
import com.stylemind.commerce.repository.catalog.CategoryRepository;
import com.stylemind.commerce.service.catalog.CatalogReferenceService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CatalogReferenceServiceImpl implements CatalogReferenceService {

    private final CategoryRepository categories;
    private final BrandRepository brands;

    @Transactional(readOnly = true)
    public CatalogReferenceListResponse list(CatalogReferenceType type) {
        return new CatalogReferenceListResponse(type == CatalogReferenceType.CATEGORY
                ? categories.findAllByOrderByNameAscCategoryIdAsc().stream().map(this::response).toList()
                : brands.findAllByOrderByNameAscBrandIdAsc().stream().map(this::response).toList());
    }

    @Transactional(readOnly = true)
    public CatalogReferenceResponse get(CatalogReferenceType type, UUID id) {
        if (type == CatalogReferenceType.CATEGORY) {
            return categories.findById(id).map(this::response).orElseThrow(this::notFound);
        }
        return brands.findById(id).map(this::response).orElseThrow(this::notFound);
    }

    @Transactional
    public CatalogReferenceResponse create(CatalogReferenceType type, CatalogReferenceRequest command) {
        try {
            if (type == CatalogReferenceType.CATEGORY) {
                Category entity = new Category(); entity.setCategoryId(UUID.randomUUID()); apply(entity, command);
                return response(categories.saveAndFlush(entity));
            }
            Brand entity = new Brand(); entity.setBrandId(UUID.randomUUID()); apply(entity, command);
            return response(brands.saveAndFlush(entity));
        } catch (DataIntegrityViolationException exception) {
            throw duplicateSlug();
        }
    }

    @Transactional
    public CatalogReferenceResponse replace(CatalogReferenceType type, UUID id, CatalogReferenceRequest command) {
        try {
            if (type == CatalogReferenceType.CATEGORY) {
                Category entity = categories.findById(id).orElseThrow(this::notFound); apply(entity, command);
                return response(categories.saveAndFlush(entity));
            }
            Brand entity = brands.findById(id).orElseThrow(this::notFound); apply(entity, command);
            return response(brands.saveAndFlush(entity));
        } catch (DataIntegrityViolationException exception) {
            throw duplicateSlug();
        }
    }

    @Transactional
    public void delete(CatalogReferenceType type, UUID id) {
        boolean exists = type == CatalogReferenceType.CATEGORY ? categories.existsById(id) : brands.existsById(id);
        if (!exists) {
            throw notFound();
        }
        if (type == CatalogReferenceType.CATEGORY ? categories.isReferenced(id) : brands.isReferenced(id)) {
            throw new ApiOperationException(HttpStatus.CONFLICT, "CATALOG_REFERENCE_IN_USE",
                    "Catalog reference is used by one or more products");
        }
        if (type == CatalogReferenceType.CATEGORY) categories.deleteById(id); else brands.deleteById(id);
    }

    private void apply(Category entity, CatalogReferenceRequest request) {
        entity.setName(request.name()); entity.setSlug(request.slug()); entity.setActive(request.is_active());
    }

    private void apply(Brand entity, CatalogReferenceRequest request) {
        entity.setName(request.name()); entity.setSlug(request.slug()); entity.setActive(request.is_active());
    }

    private CatalogReferenceResponse response(Category entity) {
        return new CatalogReferenceResponse(entity.getCategoryId(), entity.getName(), entity.getSlug(), entity.isActive());
    }

    private CatalogReferenceResponse response(Brand entity) {
        return new CatalogReferenceResponse(entity.getBrandId(), entity.getName(), entity.getSlug(), entity.isActive());
    }

    private ApiOperationException notFound() {
        return new ApiOperationException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found");
    }

    private ApiOperationException duplicateSlug() {
        return new ApiOperationException(HttpStatus.CONFLICT, "DUPLICATE_SLUG",
                "A catalog reference with this slug already exists");
    }
}
