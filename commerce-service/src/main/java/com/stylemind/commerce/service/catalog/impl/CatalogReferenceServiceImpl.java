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
    private final com.stylemind.commerce.repository.product.ProductRepository products;

    @Transactional(readOnly = true)
    public CatalogReferenceListResponse list(CatalogReferenceType type) {
        return new CatalogReferenceListResponse(type == CatalogReferenceType.CATEGORY
                ? categories.findAllByActiveTrueOrderByNameAscCategoryIdAsc().stream().map(this::response).toList()
                : brands.findAllByActiveTrueAndDeletedAtIsNullOrderByNameAscBrandIdAsc().stream().map(this::response).toList());
    }

    @Transactional(readOnly = true)
    public CatalogReferenceResponse get(CatalogReferenceType type, UUID id) {
        if (type == CatalogReferenceType.CATEGORY) {
            return categories.findByCategoryIdAndActiveTrue(id).map(this::response).orElseThrow(this::notFound);
        }
        return brands.findByBrandIdAndActiveTrueAndDeletedAtIsNull(id).map(this::response).orElseThrow(this::notFound);
    }

    @Transactional(readOnly = true)
    public CatalogReferenceListResponse listAdmin(CatalogReferenceType type) {
        if (type == CatalogReferenceType.CATEGORY) {
            java.util.Map<UUID, Long> counts = new java.util.HashMap<>();
            products.countProductsByCategory().forEach(row -> counts.put((UUID) row[0], (Long) row[1]));
            return new CatalogReferenceListResponse(categories.findAllByOrderByNameAscCategoryIdAsc().stream()
                    .map(entity -> new CatalogReferenceResponse(entity.getCategoryId(), entity.getName(), entity.getSlug(),
                            entity.isActive(), counts.getOrDefault(entity.getCategoryId(), 0L)))
                    .toList());
        }
        return new CatalogReferenceListResponse(brands.findAllByDeletedAtIsNullOrderByNameAscBrandIdAsc().stream().map(this::response).toList());
    }

    @Transactional(readOnly = true)
    public CatalogReferenceResponse getAdmin(CatalogReferenceType type, UUID id) {
        if (type == CatalogReferenceType.CATEGORY) {
            return categories.findById(id).map(this::response).orElseThrow(this::notFound);
        }
        return brands.findByBrandIdAndDeletedAtIsNull(id).map(this::response).orElseThrow(this::notFound);
    }

    @Transactional
    public CatalogReferenceResponse create(CatalogReferenceType type, CatalogReferenceRequest command) {
        try {
            if (type == CatalogReferenceType.CATEGORY) {
                Category entity = new Category(); entity.setCategoryId(UUID.randomUUID()); requireUniqueName(entity.getCategoryId(), command); apply(entity, command);
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
                Category entity = categories.findById(id).orElseThrow(this::notFound); requireUniqueName(id, command); apply(entity, command);
                return response(categories.saveAndFlush(entity));
            }
            Brand entity = brands.findByBrandIdAndDeletedAtIsNull(id).orElseThrow(this::notFound); apply(entity, command);
            return response(brands.saveAndFlush(entity));
        } catch (DataIntegrityViolationException exception) {
            throw duplicateSlug();
        }
    }

    /** Permanent removal; only a category that no product (of any status) uses can go. */
    @Transactional
    public void deleteCategoryPermanently(UUID id) {
        Category entity = categories.findById(id).orElseThrow(this::notFound);
        if (products.countByCategoryId(id) > 0) {
            throw new ApiOperationException(HttpStatus.CONFLICT, "CATEGORY_HAS_PRODUCTS",
                    "Category still has products; hide it instead of deleting it");
        }
        categories.delete(entity);
        categories.flush();
    }

    /** Delete a brand without breaking historical product references. */
    @Transactional
    public void deleteBrandPermanently(UUID id) {
        Brand entity = brands.findByBrandIdAndDeletedAtIsNull(id).orElseThrow(this::notFound);
        if (products.countByBrandId(id) > 0) {
            entity.setActive(false);
            entity.setDeletedAt(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
            brands.saveAndFlush(entity);
            return;
        }
        brands.delete(entity);
        brands.flush();
    }

    @Transactional
    public void delete(CatalogReferenceType type, UUID id) {
        if (type == CatalogReferenceType.CATEGORY) {
            Category entity = categories.findById(id).orElseThrow(this::notFound);
            if (entity.isActive()) {
                entity.setActive(false);
                categories.saveAndFlush(entity);
            }
            return;
        }
        Brand entity = brands.findByBrandIdAndDeletedAtIsNull(id).orElseThrow(this::notFound);
        if (entity.isActive()) {
            entity.setActive(false);
            brands.saveAndFlush(entity);
        }
    }

    private void requireUniqueName(UUID id, CatalogReferenceRequest command) {
        if (categories.existsByNameIgnoreCaseAndCategoryIdNot(command.name(), id)) {
            throw ApiOperationException.invalidFields(java.util.List.of(
                    new com.stylemind.commerce.errors.FieldError("name", "DUPLICATE_NAME", "Đã có danh mục cùng tên")));
        }
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
