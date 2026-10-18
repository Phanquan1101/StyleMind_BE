package com.stylemind.commerce.service.product;

import com.stylemind.commerce.dto.product.ProductCommand;
import com.stylemind.commerce.dto.product.ProductListCriteria;
import com.stylemind.commerce.dto.product.ProductListResponse;
import com.stylemind.commerce.dto.product.ProductResponse;
import com.stylemind.commerce.dto.product.VariantBatchLookupResponse;
import com.stylemind.commerce.dto.product.VariantCommand;
import com.stylemind.commerce.dto.product.VariantImageResponse;
import com.stylemind.commerce.dto.product.VariantResponse;
import com.stylemind.commerce.entity.Brand;
import com.stylemind.commerce.entity.Category;
import com.stylemind.commerce.entity.Product;
import com.stylemind.commerce.entity.ProductVariant;
import com.stylemind.commerce.entity.VariantImage;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.repository.catalog.BrandRepository;
import com.stylemind.commerce.repository.catalog.CategoryRepository;
import com.stylemind.commerce.repository.product.ProductRepository;
import com.stylemind.commerce.repository.product.ProductVariantRepository;
import com.stylemind.commerce.repository.product.VariantImageRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final VariantImageRepository images;
    private final CategoryRepository categories;
    private final BrandRepository brands;
    private final VariantImageStorageService imageStorage;

    @Transactional
    public ProductResponse createProduct(ProductCommand command) {
        validateReferences(command);
        Product product = new Product();
        product.setProductId(UUID.randomUUID());
        apply(product, command);
        return productResponse(products.saveAndFlush(product), false);
    }

    @Transactional
    public ProductResponse replaceProduct(UUID productId, ProductCommand command) {
        validateReferences(command);
        Product product = products.findById(productId).orElseThrow(this::notFound);
        apply(product, command);
        return productResponse(products.saveAndFlush(product), false);
    }

    @Transactional
    public void deleteProduct(UUID productId) {
        try {
            Product product = products.findById(productId).orElseThrow(this::notFound);
            products.delete(product);
            products.flush();
        } catch (DataIntegrityViolationException exception) {
            throw conflict("PRODUCT_IN_USE", "Product is used by one or more variants or orders");
        }
    }

    @Transactional(readOnly = true)
    public ProductListResponse listPublicProducts(ProductListCriteria criteria) {
        String keyword = criteria.keyword() == null ? null
                : "%" + criteria.keyword().toLowerCase(java.util.Locale.ROOT) + "%";
        Page<Product> page = products.searchPublic(keyword, criteria.categoryId(), criteria.demographicSegment(),
                criteria.minPriceVnd(), criteria.maxPriceVnd(),
                PageRequest.of(criteria.page(), criteria.size(),
                        Sort.by(Sort.Order.asc("name"), Sort.Order.asc("productId"))));
        List<ProductResponse> items = productResponses(page.getContent(), true);
        return new ProductListResponse(items, criteria.page(), criteria.size(), page.getTotalElements(),
                page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ProductResponse getPublicProduct(UUID productId) {
        Product product = products.findById(productId)
                .filter(this::isActiveProduct)
                .orElseThrow(this::notFound);
        return productResponse(product, true);
    }

    @Transactional(readOnly = true)
    public ProductResponse getAdminProduct(UUID productId) {
        return productResponse(products.findById(productId).orElseThrow(this::notFound), false);
    }

    @Transactional
    public VariantResponse createVariant(UUID productId, VariantCommand command) {
        if (!products.existsById(productId)) {
            throw notFound();
        }
        ProductVariant variant = new ProductVariant();
        variant.setVariantId(UUID.randomUUID());
        variant.setProductId(productId);
        apply(variant, command);
        try {
            return variantResponse(variants.saveAndFlush(variant), List.of());
        } catch (DataIntegrityViolationException exception) {
            throw conflict("DUPLICATE_SKU", "A product variant with this SKU already exists");
        }
    }

    @Transactional
    public VariantResponse replaceVariant(UUID variantId, VariantCommand command) {
        ProductVariant variant = variants.findById(variantId).orElseThrow(this::notFound);
        apply(variant, command);
        try {
            return variantResponse(variants.saveAndFlush(variant), imageResponses(variantId));
        } catch (DataIntegrityViolationException exception) {
            throw conflict("DUPLICATE_SKU", "A product variant with this SKU already exists");
        }
    }

    @Transactional
    public void deleteVariant(UUID variantId) {
        ProductVariant variant = variants.findById(variantId).orElseThrow(this::notFound);
        variants.delete(variant);
        variants.flush();
    }

    @Transactional(readOnly = true)
    public VariantBatchLookupResponse activeVariantLookup(List<UUID> variantIds) {
        List<ProductVariant> active = variants.findByVariantIdInOrderByVariantIdAsc(variantIds).stream()
                .filter(variant -> "ACTIVE".equals(variant.getVariantStatus()))
                .filter(variant -> products.findById(variant.getProductId()).filter(this::isActiveProduct).isPresent())
                .toList();
        Map<UUID, List<VariantImageResponse>> imagesByVariant = imageResponsesByVariant(active);
        return new VariantBatchLookupResponse(active.stream()
                .map(variant -> variantResponse(variant,
                        imagesByVariant.getOrDefault(variant.getVariantId(), List.of())))
                .toList());
    }

    @Transactional
    public VariantImageResponse uploadImage(UUID variantId, MultipartFile file, String altText) {
        if (variants.findById(variantId).isEmpty()) {
            throw notFound();
        }
        UUID imageId = UUID.randomUUID();
        String imageUrl = imageStorage.store(imageId, file);
        int displayOrder = images.findTopByVariantIdOrderByDisplayOrderDesc(variantId)
                .map(image -> image.getDisplayOrder() + 1).orElse(0);
        VariantImage image = new VariantImage();
        image.setImageId(imageId);
        image.setVariantId(variantId);
        image.setImageUrl(imageUrl);
        image.setAltText(altText);
        image.setDisplayOrder(displayOrder);
        image.setPrimary(images.countByVariantId(variantId) == 0);
        try {
            return imageResponse(images.saveAndFlush(image));
        } catch (RuntimeException exception) {
            imageStorage.delete(imageId);
            throw exception;
        }
    }

    @Transactional
    public List<VariantImageResponse> reorderImages(UUID variantId, Map<UUID, Integer> order, UUID primaryImageId) {
        if (variants.findById(variantId).isEmpty()) {
            throw notFound();
        }
        List<VariantImage> existing = images.findByVariantIdInOrderByVariantIdAscDisplayOrderAscImageIdAsc(
                List.of(variantId));
        Map<UUID, VariantImage> byId = existing.stream()
                .collect(java.util.stream.Collectors.toMap(VariantImage::getImageId, image -> image));
        if (!byId.keySet().containsAll(order.keySet())
                || (primaryImageId != null && !byId.containsKey(primaryImageId))) {
            throw notFound();
        }
        existing.forEach(image -> image.setPrimary(false));
        int temporary = 100_000;
        for (UUID imageId : order.keySet()) {
            byId.get(imageId).setDisplayOrder(temporary++);
        }
        images.saveAllAndFlush(existing);
        order.forEach((imageId, displayOrder) -> byId.get(imageId).setDisplayOrder(displayOrder));
        if (primaryImageId != null) {
            byId.get(primaryImageId).setPrimary(true);
        }
        images.saveAllAndFlush(existing);
        return existing.stream().sorted(Comparator.comparingInt(VariantImage::getDisplayOrder)
                        .thenComparing(VariantImage::getImageId))
                .map(this::imageResponse).toList();
    }

    @Transactional
    public void deleteImage(UUID imageId) {
        VariantImage image = images.findById(imageId).orElseThrow(this::notFound);
        images.delete(image);
        images.flush();
        imageStorage.delete(image.getImageId());
    }

    private void validateReferences(ProductCommand command) {
        if (!categories.findById(command.categoryId()).map(Category::isActive).orElse(false)) {
            throw conflict("CATEGORY_NOT_ACTIVE", "Category does not exist or is inactive");
        }
        if (!brands.findById(command.brandId()).map(Brand::isActive).orElse(false)) {
            throw conflict("BRAND_NOT_ACTIVE", "Brand does not exist or is inactive");
        }
    }

    private boolean isActiveProduct(Product product) {
        return "ACTIVE".equals(product.getProductStatus())
                && categories.findById(product.getCategoryId()).map(Category::isActive).orElse(false)
                && brands.findById(product.getBrandId()).map(Brand::isActive).orElse(false);
    }

    private ProductResponse productResponse(Product product, boolean activeVariantsOnly) {
        return productResponses(List.of(product), activeVariantsOnly).getFirst();
    }

    private List<ProductResponse> productResponses(List<Product> products, boolean activeVariantsOnly) {
        List<UUID> productIds = products.stream().map(Product::getProductId).toList();
        List<ProductVariant> productVariants = variants.findByProductIdInOrderByProductIdAscSkuAscVariantIdAsc(productIds)
                .stream().filter(variant -> !activeVariantsOnly || "ACTIVE".equals(variant.getVariantStatus())).toList();
        Map<UUID, List<VariantImageResponse>> imageMap = imageResponsesByVariant(productVariants);
        Map<UUID, List<VariantResponse>> variantsByProduct = new LinkedHashMap<>();
        for (ProductVariant variant : productVariants) {
            variantsByProduct.computeIfAbsent(variant.getProductId(), ignored -> new ArrayList<>())
                    .add(variantResponse(variant, imageMap.getOrDefault(variant.getVariantId(), List.of())));
        }
        return products.stream().map(product -> {
            Category category = categories.findById(product.getCategoryId()).orElseThrow(this::notFound);
            Brand brand = brands.findById(product.getBrandId()).orElseThrow(this::notFound);
            return new ProductResponse(product.getProductId(), product.getCategoryId(), product.getBrandId(),
                    category.getName(), brand.getName(), product.getName(), product.getDescription(),
                    product.getDemographicSegment(), product.getProductStatus(),
                    variantsByProduct.getOrDefault(product.getProductId(), List.of()));
        }).toList();
    }

    private Map<UUID, List<VariantImageResponse>> imageResponsesByVariant(Collection<ProductVariant> productVariants) {
        if (productVariants.isEmpty()) {
            return Map.of();
        }
        List<UUID> variantIds = productVariants.stream().map(ProductVariant::getVariantId).toList();
        Map<UUID, List<VariantImageResponse>> result = new LinkedHashMap<>();
        for (VariantImage image : images.findByVariantIdInOrderByVariantIdAscDisplayOrderAscImageIdAsc(variantIds)) {
            result.computeIfAbsent(image.getVariantId(), ignored -> new ArrayList<>()).add(imageResponse(image));
        }
        return result;
    }

    private List<VariantImageResponse> imageResponses(UUID variantId) {
        return images.findByVariantIdInOrderByVariantIdAscDisplayOrderAscImageIdAsc(List.of(variantId)).stream()
                .map(this::imageResponse).toList();
    }

    private VariantResponse variantResponse(ProductVariant variant, List<VariantImageResponse> imageResponses) {
        return new VariantResponse(variant.getVariantId(), variant.getProductId(), variant.getSku(), variant.getSize(),
                variant.getColor(), variant.getMaterial(), variant.getUnitPriceVnd(), variant.getVariantStatus(),
                imageResponses);
    }

    private VariantImageResponse imageResponse(VariantImage image) {
        return new VariantImageResponse(image.getImageId(), image.getVariantId(), image.getImageUrl(), image.getAltText(),
                image.getDisplayOrder(), image.isPrimary());
    }

    private void apply(Product product, ProductCommand command) {
        product.setCategoryId(command.categoryId());
        product.setBrandId(command.brandId());
        product.setName(command.name());
        product.setDescription(command.description());
        product.setDemographicSegment(command.demographicSegment());
        product.setProductStatus(command.productStatus().name());
    }

    private void apply(ProductVariant variant, VariantCommand command) {
        variant.setSku(command.sku());
        variant.setSize(command.size());
        variant.setColor(command.color());
        variant.setMaterial(command.material());
        variant.setUnitPriceVnd(command.unitPriceVnd());
        variant.setVariantStatus(command.variantStatus().name());
    }

    private ApiOperationException notFound() {
        return new ApiOperationException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found");
    }

    private ApiOperationException conflict(String code, String detail) {
        return new ApiOperationException(HttpStatus.CONFLICT, code, detail);
    }
}
