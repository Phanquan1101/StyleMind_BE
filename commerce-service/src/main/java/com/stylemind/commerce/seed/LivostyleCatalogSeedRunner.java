package com.stylemind.commerce.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.commerce.entity.Brand;
import com.stylemind.commerce.entity.Category;
import com.stylemind.commerce.entity.ImageAsset;
import com.stylemind.commerce.entity.Product;
import com.stylemind.commerce.entity.ProductVariant;
import com.stylemind.commerce.entity.VariantImage;
import com.stylemind.commerce.repository.catalog.BrandRepository;
import com.stylemind.commerce.repository.catalog.CategoryRepository;
import com.stylemind.commerce.repository.product.ImageAssetRepository;
import com.stylemind.commerce.repository.product.ProductRepository;
import com.stylemind.commerce.repository.product.ProductVariantRepository;
import com.stylemind.commerce.repository.product.VariantImageRepository;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "stylemind.commerce.catalog-seed.enabled", havingValue = "true")
public class LivostyleCatalogSeedRunner implements ApplicationRunner {
    private static final String RESOURCE = "db/seed/clothing-200-db-payload-vi.json";
    private static final int INITIAL_QUANTITY = 20;
    private final ObjectMapper mapper;
    private final CategoryRepository categories;
    private final BrandRepository brands;
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final ImageAssetRepository assets;
    private final VariantImageRepository images;

    public LivostyleCatalogSeedRunner(ObjectMapper mapper, CategoryRepository categories, BrandRepository brands,
            ProductRepository products, ProductVariantRepository variants, ImageAssetRepository assets,
            VariantImageRepository images) {
        this.mapper = mapper; this.categories = categories; this.brands = brands; this.products = products;
        this.variants = variants; this.assets = assets; this.images = images;
    }

    record Payload(int version, BrandSeed brand, List<CategorySeed> categories, List<ProductSeed> products) { }
    record BrandSeed(UUID id, String name, String slug) { }
    record CategorySeed(UUID id, String name, String slug) { }
    record ProductSeed(UUID id, String categorySlug, String name, String description, String demographic,
            List<VariantSeed> variants, List<ImageSeed> images) { }
    record VariantSeed(UUID id, String sku, String size, String color, String material, long price) { }
    record ImageSeed(UUID assetId, String url, String alt, int order, boolean primary) { }

    @Override @Transactional
    public void run(ApplicationArguments arguments) {
        if (products.count() != 0) return;
        Payload payload = read();
        if (payload.version() != 1 || payload.products().size() != 200) {
            throw new IllegalStateException("Unexpected Livostyle seed payload");
        }
        Brand brand = new Brand(); brand.setBrandId(payload.brand().id()); brand.setName(payload.brand().name());
        brand.setSlug(payload.brand().slug()); brand.setActive(true); brands.save(brand);
        java.util.Map<String, UUID> categoryIds = new HashMap<>();
        for (CategorySeed source : payload.categories()) {
            Category category = new Category(); category.setCategoryId(source.id()); category.setName(source.name());
            category.setSlug(source.slug()); category.setActive(true); categories.save(category);
            categoryIds.put(source.slug(), source.id());
        }
        OffsetDateTime now = OffsetDateTime.now(); Set<UUID> storedAssets = new HashSet<>();
        for (ProductSeed source : payload.products()) {
            Product product = new Product(); product.setProductId(source.id());
            UUID categoryId = categoryIds.get(source.categorySlug());
            if (categoryId == null) throw new IllegalStateException("Seed product has an unknown category");
            product.setCategoryId(categoryId);
            product.setBrandId(brand.getBrandId()); product.setName(source.name()); product.setDescription(source.description());
            product.setDemographicSegment(source.demographic()); product.setProductStatus("ACTIVE"); products.save(product);
            for (ImageSeed sourceImage : source.images()) if (storedAssets.add(sourceImage.assetId())) {
                ImageAsset asset = new ImageAsset(); asset.setImageAssetId(sourceImage.assetId()); asset.setImageUrl(sourceImage.url());
                asset.setCreatedAt(now); assets.save(asset);
            }
            for (VariantSeed sourceVariant : source.variants()) {
                ProductVariant variant = new ProductVariant(); variant.setVariantId(sourceVariant.id()); variant.setProductId(product.getProductId());
                variant.setSku(sourceVariant.sku()); variant.setSize(sourceVariant.size()); variant.setColor(sourceVariant.color());
                variant.setMaterial(sourceVariant.material()); variant.setUnitPriceVnd(sourceVariant.price());
                variant.setQuantity(INITIAL_QUANTITY); variant.setVariantStatus("ACTIVE"); variants.save(variant);
                for (ImageSeed sourceImage : source.images()) {
                    VariantImage image = new VariantImage(); image.setImageId(UUID.nameUUIDFromBytes((sourceVariant.id()+":"+sourceImage.assetId()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    image.setVariantId(variant.getVariantId()); image.setImageAssetId(sourceImage.assetId()); image.setAltText(sourceImage.alt());
                    image.setDisplayOrder(sourceImage.order()); image.setPrimary(sourceImage.primary()); images.save(image);
                }
            }
        }
    }

    private Payload read() {
        try (InputStream input = new ClassPathResource(RESOURCE).getInputStream()) { return mapper.readValue(input, Payload.class); }
        catch (Exception exception) { throw new IllegalStateException("Livostyle seed payload could not be read", exception); }
    }
}
