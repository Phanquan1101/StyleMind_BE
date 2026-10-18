package com.stylemind.commerce.service.product;

import com.stylemind.commerce.dto.product.ProductListResponse;
import com.stylemind.commerce.dto.product.ProductResponse;
import com.stylemind.commerce.dto.product.VariantBatchLookupResponse;
import com.stylemind.commerce.dto.product.VariantImageResponse;
import com.stylemind.commerce.dto.product.VariantResponse;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.model.product.Product;
import com.stylemind.commerce.model.product.ProductCommand;
import com.stylemind.commerce.model.product.ProductListCriteria;
import com.stylemind.commerce.model.product.ProductPage;
import com.stylemind.commerce.model.product.ProductVariant;
import com.stylemind.commerce.model.product.VariantCommand;
import com.stylemind.commerce.model.product.VariantImage;
import com.stylemind.commerce.repository.product.ProductRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository repository;
    private final VariantImageStorageService imageStorage;

    @Transactional
    public ProductResponse createProduct(ProductCommand command) {
        validateReferences(command);
        return productResponse(repository.insertProduct(UUID.randomUUID(), command), false);
    }

    @Transactional
    public ProductResponse replaceProduct(UUID productId, ProductCommand command) {
        validateReferences(command);
        Product product = repository.updateProduct(productId, command).orElseThrow(this::notFound);
        return productResponse(product, false);
    }

    @Transactional
    public void deleteProduct(UUID productId) {
        try {
            if (repository.deleteProduct(productId) != 1) {
                throw notFound();
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict("PRODUCT_IN_USE", "Product is used by one or more variants or orders");
        }
    }

    @Transactional(readOnly = true)
    public ProductListResponse listPublicProducts(ProductListCriteria criteria) {
        ProductPage page = repository.publicProductPage(criteria);
        List<ProductResponse> items = productResponses(page.items(), true);
        long totalPages = page.totalElements() == 0 ? 0
                : (page.totalElements() + criteria.size() - 1) / criteria.size();
        return new ProductListResponse(items, criteria.page(), criteria.size(), page.totalElements(), totalPages);
    }

    @Transactional(readOnly = true)
    public ProductResponse getPublicProduct(UUID productId) {
        Product product = repository.findActiveProduct(productId).orElseThrow(this::notFound);
        return productResponse(product, true);
    }

    @Transactional(readOnly = true)
    public ProductResponse getAdminProduct(UUID productId) {
        return productResponse(repository.findProduct(productId).orElseThrow(this::notFound), false);
    }

    @Transactional
    public VariantResponse createVariant(UUID productId, VariantCommand command) {
        if (repository.findProduct(productId).isEmpty()) {
            throw notFound();
        }
        try {
            ProductVariant variant = repository.insertVariant(productId, UUID.randomUUID(), command);
            return variantResponse(variant, List.of());
        } catch (DuplicateKeyException exception) {
            throw conflict("DUPLICATE_SKU", "A product variant with this SKU already exists");
        }
    }

    @Transactional
    public VariantResponse replaceVariant(UUID variantId, VariantCommand command) {
        try {
            ProductVariant variant = repository.updateVariant(variantId, command).orElseThrow(this::notFound);
            return variantResponse(variant, repository.imagesForVariants(List.of(variant.variantId())));
        } catch (DuplicateKeyException exception) {
            throw conflict("DUPLICATE_SKU", "A product variant with this SKU already exists");
        }
    }

    @Transactional
    public void deleteVariant(UUID variantId) {
        if (repository.deleteVariant(variantId) != 1) {
            throw notFound();
        }
    }

    @Transactional(readOnly = true)
    public VariantBatchLookupResponse activeVariantLookup(List<UUID> variantIds) {
        List<ProductVariant> variants = repository.activeVariants(variantIds);
        Map<UUID, List<VariantImage>> images = imagesByVariant(variants);
        return new VariantBatchLookupResponse(variants.stream()
                .map(variant -> variantResponse(variant, images.getOrDefault(variant.variantId(), List.of())))
                .toList());
    }

    @Transactional
    public VariantImageResponse uploadImage(UUID variantId, MultipartFile file, String altText) {
        ProductVariant variant = repository.findVariant(variantId).orElseThrow(this::notFound);
        UUID imageId = UUID.randomUUID();
        String imageUrl = imageStorage.store(imageId, file);
        int displayOrder = repository.nextImageDisplayOrder(variant.variantId());
        boolean primary = !repository.variantHasImages(variant.variantId());
        try {
            return imageResponse(repository.insertImage(imageId, variant.variantId(), imageUrl,
                    altText, displayOrder, primary));
        } catch (RuntimeException exception) {
            imageStorage.delete(imageId);
            throw exception;
        }
    }

    @Transactional
    public List<VariantImageResponse> reorderImages(UUID variantId, Map<UUID, Integer> order, UUID primaryImageId) {
        ProductVariant variant = repository.findVariant(variantId).orElseThrow(this::notFound);
        List<VariantImage> existing = repository.imagesForVariants(List.of(variant.variantId()));
        Map<UUID, VariantImage> byId = existing.stream()
                .collect(Collectors.toMap(VariantImage::imageId, image -> image));
        if (!byId.keySet().containsAll(order.keySet())
                || (primaryImageId != null && !byId.containsKey(primaryImageId))) {
            throw notFound();
        }
        repository.reorderImages(variant.variantId(), order, primaryImageId);
        return repository.imagesForVariants(List.of(variant.variantId())).stream()
                .map(this::imageResponse).toList();
    }

    @Transactional
    public void deleteImage(UUID imageId) {
        VariantImage image = repository.findImage(imageId).orElseThrow(this::notFound);
        if (repository.deleteImage(imageId) != 1) {
            throw notFound();
        }
        imageStorage.delete(image.imageId());
    }

    private void validateReferences(ProductCommand command) {
        if (!repository.activeCategoryExists(command.categoryId())) {
            throw conflict("CATEGORY_NOT_ACTIVE", "Category does not exist or is inactive");
        }
        if (!repository.activeBrandExists(command.brandId())) {
            throw conflict("BRAND_NOT_ACTIVE", "Brand does not exist or is inactive");
        }
    }

    private ProductResponse productResponse(Product product, boolean activeVariantsOnly) {
        return productResponses(List.of(product), activeVariantsOnly).getFirst();
    }

    private List<ProductResponse> productResponses(List<Product> products, boolean activeVariantsOnly) {
        List<UUID> ids = products.stream().map(Product::productId).toList();
        List<ProductVariant> variants = repository.variantsForProducts(ids, activeVariantsOnly);
        Map<UUID, List<VariantImage>> images = imagesByVariant(variants);
        Map<UUID, List<VariantResponse>> variantsByProduct = new LinkedHashMap<>();
        for (ProductVariant variant : variants) {
            variantsByProduct.computeIfAbsent(variant.productId(), ignored -> new ArrayList<>())
                    .add(variantResponse(variant, images.getOrDefault(variant.variantId(), List.of())));
        }
        return products.stream()
                .map(product -> new ProductResponse(product.productId(), product.categoryId(), product.brandId(),
                        product.categoryName(), product.brandName(), product.name(), product.description(),
                        product.demographicSegment(), product.productStatus().name(),
                        variantsByProduct.getOrDefault(product.productId(), List.of())))
                .toList();
    }

    private Map<UUID, List<VariantImage>> imagesByVariant(Collection<ProductVariant> variants) {
        List<UUID> variantIds = variants.stream().map(ProductVariant::variantId).toList();
        Map<UUID, List<VariantImage>> result = new LinkedHashMap<>();
        for (VariantImage image : repository.imagesForVariants(variantIds)) {
            result.computeIfAbsent(image.variantId(), ignored -> new ArrayList<>()).add(image);
        }
        result.values().forEach(images -> images.sort(Comparator.comparingInt(VariantImage::displayOrder)
                .thenComparing(VariantImage::imageId)));
        return result;
    }

    private VariantResponse variantResponse(ProductVariant variant, List<VariantImage> images) {
        return new VariantResponse(variant.variantId(), variant.productId(), variant.sku(), variant.size(),
                variant.color(), variant.material(), variant.unitPriceVnd(), variant.variantStatus().name(),
                images.stream().map(this::imageResponse).toList());
    }

    private VariantImageResponse imageResponse(VariantImage image) {
        return new VariantImageResponse(image.imageId(), image.variantId(), image.imageUrl(), image.altText(),
                image.displayOrder(), image.primary());
    }

    private ApiOperationException notFound() {
        return new ApiOperationException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found");
    }

    private ApiOperationException conflict(String code, String detail) {
        return new ApiOperationException(HttpStatus.CONFLICT, code, detail);
    }
}
