package com.stylemind.commerce.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "stylemind.commerce.catalog-seed.enabled", havingValue = "true")
public class DevelopmentCatalogSeedRunner implements ApplicationRunner {
    private static final String RESOURCE = "db/seed/development-catalog.json";
    private final ObjectMapper mapper;
    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;

    public DevelopmentCatalogSeedRunner(ObjectMapper mapper, JdbcClient jdbc, TransactionTemplate transactions) {
        this.mapper = mapper;
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    record Manifest(int version, int sourceImageCount, List<CategorySeed> categories, List<BrandSeed> brands,
            List<ProductSeed> products) { }
    record CategorySeed(UUID categoryId, String name, String slug) { }
    record BrandSeed(UUID brandId, String name, String slug) { }
    record ProductSeed(UUID productId, UUID categoryId, UUID brandId, String name, String description,
            String demographicSegment, List<VariantSeed> variants) { }
    record VariantSeed(UUID variantId, String sku, String size, String color, String material,
            long unitPriceVnd, int onHandQuantity, List<ImageSeed> images) { }
    record ImageSeed(UUID imageId, UUID imageAssetId, String imageUrl, String altText,
            int displayOrder, boolean primary) { }

    @Override
    public void run(ApplicationArguments args) {
        Manifest manifest = readManifest();
        int imageCount = manifest.products().stream().flatMap(product -> product.variants().stream())
                .flatMap(variant -> variant.images().stream()).map(ImageSeed::imageAssetId).collect(
                        java.util.stream.Collectors.toSet()).size();
        if (manifest.version() != 2 || manifest.products().isEmpty() || manifest.sourceImageCount() != 600
                || imageCount != manifest.sourceImageCount()) {
            throw new IllegalStateException("Development catalog manifest has an unexpected shape");
        }
        transactions.executeWithoutResult(status -> seed(manifest));
    }

    private Manifest readManifest() {
        try (InputStream input = new ClassPathResource(RESOURCE).getInputStream()) {
            return mapper.readValue(input, Manifest.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Development catalog manifest could not be read", exception);
        }
    }

    private void seed(Manifest manifest) {
        OffsetDateTime now = OffsetDateTime.now();
        // Retire rows from older versions first. Upserts below reactivate every canonical
        // product and variant in the current manifest without breaking historical FKs.
        jdbc.sql("""
                UPDATE public.product SET product_status='INACTIVE' WHERE product_id IN (
                    SELECT DISTINCT product_id FROM public.product_variant WHERE sku LIKE 'MYNTRA-%'
                )
                """)
                .update();
        jdbc.sql("UPDATE public.product_variant SET variant_status='INACTIVE' WHERE sku LIKE 'MYNTRA-%'").update();
        manifest.categories().forEach(category -> jdbc.sql("""
                INSERT INTO public.category(category_id,name,slug,is_active) VALUES (:id,:name,:slug,true)
                ON CONFLICT (category_id) DO UPDATE SET name=EXCLUDED.name,slug=EXCLUDED.slug,is_active=true
                """).param("id", category.categoryId()).param("name", category.name())
                .param("slug", category.slug()).update());
        manifest.brands().forEach(brand -> jdbc.sql("""
                INSERT INTO public.brand(brand_id,name,slug,is_active) VALUES (:id,:name,:slug,true)
                ON CONFLICT (brand_id) DO UPDATE SET name=EXCLUDED.name,slug=EXCLUDED.slug,is_active=true
                """).param("id", brand.brandId()).param("name", brand.name()).param("slug", brand.slug()).update());
        for (ProductSeed product : manifest.products()) {
            jdbc.sql("""
                    INSERT INTO public.product(product_id,category_id,brand_id,name,description,demographic_segment,product_status)
                    VALUES (:id,:category,:brand,:name,:description,:demographic,'ACTIVE')
                    ON CONFLICT (product_id) DO UPDATE SET category_id=EXCLUDED.category_id,brand_id=EXCLUDED.brand_id,
                        name=EXCLUDED.name,description=EXCLUDED.description,
                        demographic_segment=EXCLUDED.demographic_segment,product_status='ACTIVE'
                    """).param("id", product.productId()).param("category", product.categoryId())
                    .param("brand", product.brandId()).param("name", product.name())
                    .param("description", product.description()).param("demographic", product.demographicSegment()).update();
            for (VariantSeed variant : product.variants()) {
                jdbc.sql("""
                        INSERT INTO public.product_variant(
                            variant_id,product_id,sku,size,color,material,unit_price_vnd,variant_status)
                        VALUES (:id,:product,:sku,:size,:color,:material,:price,'ACTIVE')
                        ON CONFLICT (variant_id) DO UPDATE SET product_id=EXCLUDED.product_id,sku=EXCLUDED.sku,
                            size=EXCLUDED.size,color=EXCLUDED.color,material=EXCLUDED.material,
                            unit_price_vnd=EXCLUDED.unit_price_vnd,variant_status='ACTIVE'
                        """).param("id", variant.variantId()).param("product", product.productId())
                        .param("sku", variant.sku()).param("size", variant.size()).param("color", variant.color())
                        .param("material", variant.material()).param("price", variant.unitPriceVnd()).update();
                jdbc.sql("""
                        INSERT INTO public.variant_inventory(variant_id,on_hand_quantity,reserved_quantity,updated_at)
                        VALUES (:id,:quantity,0,:now) ON CONFLICT (variant_id) DO NOTHING
                        """).param("id", variant.variantId()).param("quantity", variant.onHandQuantity())
                        .param("now", now).update();
                for (ImageSeed image : variant.images()) {
                    jdbc.sql("""
                            INSERT INTO public.image_asset(image_asset_id,image_url,created_at) VALUES (:id,:url,:now)
                            ON CONFLICT (image_asset_id) DO UPDATE SET image_url=EXCLUDED.image_url
                            """).param("id", image.imageAssetId()).param("url", image.imageUrl())
                            .param("now", now).update();
                    jdbc.sql("""
                            INSERT INTO public.variant_image(
                                image_id,variant_id,image_asset_id,alt_text,display_order,is_primary)
                            VALUES (:image,:variant,:asset,:alt,:displayOrder,:primary)
                            ON CONFLICT (image_id) DO UPDATE SET variant_id=EXCLUDED.variant_id,
                                image_asset_id=EXCLUDED.image_asset_id,alt_text=EXCLUDED.alt_text,
                                display_order=EXCLUDED.display_order,is_primary=EXCLUDED.is_primary
                            """).param("image", image.imageId()).param("variant", variant.variantId())
                            .param("asset", image.imageAssetId()).param("alt", image.altText())
                            .param("displayOrder", image.displayOrder()).param("primary", image.primary()).update();
                }
            }
        }
    }
}
