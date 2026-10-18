package com.stylemind.commerce.repository.product;

import com.stylemind.commerce.model.product.Product;
import com.stylemind.commerce.model.product.ProductCommand;
import com.stylemind.commerce.model.product.ProductListCriteria;
import com.stylemind.commerce.model.product.ProductPage;
import com.stylemind.commerce.model.product.ProductStatus;
import com.stylemind.commerce.model.product.ProductVariant;
import com.stylemind.commerce.model.product.ProductVariantStatus;
import com.stylemind.commerce.model.product.VariantCommand;
import com.stylemind.commerce.model.product.VariantImage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public boolean activeCategoryExists(UUID categoryId) {
        return exists("SELECT EXISTS (SELECT 1 FROM public.category WHERE category_id = :id AND is_active)",
                categoryId);
    }

    public boolean activeBrandExists(UUID brandId) {
        return exists("SELECT EXISTS (SELECT 1 FROM public.brand WHERE brand_id = :id AND is_active)", brandId);
    }

    public Product insertProduct(UUID productId, ProductCommand command) {
        jdbc.update("""
                INSERT INTO public.product (
                    product_id, category_id, brand_id, name, description, demographic_segment, product_status
                ) VALUES (:productId, :categoryId, :brandId, :name, :description, :demographic, :status)
                """, productParameters(productId, command));
        return findProduct(productId).orElseThrow();
    }

    public Optional<Product> updateProduct(UUID productId, ProductCommand command) {
        int rows = jdbc.update("""
                UPDATE public.product
                SET category_id = :categoryId, brand_id = :brandId, name = :name, description = :description,
                    demographic_segment = :demographic, product_status = :status
                WHERE product_id = :productId
                """, productParameters(productId, command));
        return rows == 0 ? Optional.empty() : findProduct(productId);
    }

    public int deleteProduct(UUID productId) {
        return jdbc.update("DELETE FROM public.product WHERE product_id = :productId",
                new MapSqlParameterSource("productId", productId));
    }

    public Optional<Product> findProduct(UUID productId) {
        return jdbc.query("""
                SELECT p.product_id, p.category_id, p.brand_id, c.name AS category_name, b.name AS brand_name,
                       p.name, p.description, p.demographic_segment, p.product_status
                FROM public.product p
                JOIN public.category c ON c.category_id = p.category_id
                JOIN public.brand b ON b.brand_id = p.brand_id
                WHERE p.product_id = :productId
                """, new MapSqlParameterSource("productId", productId), rs ->
                rs.next() ? Optional.of(product(rs)) : Optional.empty());
    }

    public Optional<Product> findActiveProduct(UUID productId) {
        return jdbc.query("""
                SELECT p.product_id, p.category_id, p.brand_id, c.name AS category_name, b.name AS brand_name,
                       p.name, p.description, p.demographic_segment, p.product_status
                FROM public.product p
                JOIN public.category c ON c.category_id = p.category_id AND c.is_active
                JOIN public.brand b ON b.brand_id = p.brand_id AND b.is_active
                WHERE p.product_id = :productId AND p.product_status = 'ACTIVE'
                """, new MapSqlParameterSource("productId", productId), rs ->
                rs.next() ? Optional.of(product(rs)) : Optional.empty());
    }

    public ProductPage publicProductPage(ProductListCriteria criteria) {
        MapSqlParameterSource parameters = listParameters(criteria);
        return jdbc.query("""
                WITH filtered AS MATERIALIZED (
                    SELECT DISTINCT p.product_id, p.category_id, p.brand_id, c.name AS category_name,
                           b.name AS brand_name, p.name, p.description, p.demographic_segment, p.product_status
                    FROM public.product p
                    JOIN public.category c ON c.category_id = p.category_id AND c.is_active
                    JOIN public.brand b ON b.brand_id = p.brand_id AND b.is_active
                    WHERE p.product_status = 'ACTIVE'
                      AND (CAST(:keyword AS text) IS NULL OR lower(p.name) LIKE CAST(:keyword AS text) ESCAPE '\\'
                           OR lower(COALESCE(p.description, '')) LIKE CAST(:keyword AS text) ESCAPE '\\')
                      AND (CAST(:categoryId AS uuid) IS NULL OR p.category_id = CAST(:categoryId AS uuid))
                      AND (CAST(:demographic AS text) IS NULL OR p.demographic_segment = CAST(:demographic AS text))
                      AND (CAST(:minPrice AS bigint) IS NULL OR EXISTS (
                          SELECT 1 FROM public.product_variant v
                          WHERE v.product_id = p.product_id AND v.variant_status = 'ACTIVE'
                            AND v.unit_price_vnd >= CAST(:minPrice AS bigint)
                      ))
                      AND (CAST(:maxPrice AS bigint) IS NULL OR EXISTS (
                          SELECT 1 FROM public.product_variant v
                          WHERE v.product_id = p.product_id AND v.variant_status = 'ACTIVE'
                            AND v.unit_price_vnd <= CAST(:maxPrice AS bigint)
                      ))
                ),
                page AS MATERIALIZED (
                    SELECT * FROM filtered ORDER BY name ASC, product_id ASC LIMIT :size OFFSET :offset
                ),
                total AS (SELECT count(*) AS total_elements FROM filtered)
                SELECT total.total_elements, page.*
                FROM total LEFT JOIN page ON true
                ORDER BY page.name ASC NULLS LAST, page.product_id ASC NULLS LAST
                """, parameters, rs -> {
                    long total = 0;
                    java.util.ArrayList<Product> items = new java.util.ArrayList<>();
                    while (rs.next()) {
                        total = rs.getLong("total_elements");
                        if (rs.getObject("product_id") != null) {
                            items.add(product(rs));
                        }
                    }
                    return new ProductPage(total, items);
                });
    }

    public ProductVariant insertVariant(UUID productId, UUID variantId, VariantCommand command) {
        jdbc.update("""
                INSERT INTO public.product_variant (
                    variant_id, product_id, sku, size, color, material, unit_price_vnd, variant_status
                ) VALUES (:variantId, :productId, :sku, :size, :color, :material, :price, :status)
                """, variantParameters(productId, variantId, command));
        return findVariant(variantId).orElseThrow();
    }

    public Optional<ProductVariant> updateVariant(UUID variantId, VariantCommand command) {
        int rows = jdbc.update("""
                UPDATE public.product_variant
                SET sku = :sku, size = :size, color = :color, material = :material,
                    unit_price_vnd = :price, variant_status = :status
                WHERE variant_id = :variantId
                """, variantParameters(null, variantId, command));
        return rows == 0 ? Optional.empty() : findVariant(variantId);
    }

    public int deleteVariant(UUID variantId) {
        return jdbc.update("DELETE FROM public.product_variant WHERE variant_id = :variantId",
                new MapSqlParameterSource("variantId", variantId));
    }

    public List<ProductVariant> variantsForProducts(Collection<UUID> productIds, boolean activeOnly) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT variant_id, product_id, sku, size, color, material, unit_price_vnd, variant_status
                FROM public.product_variant
                WHERE product_id IN (:productIds)
                  AND (:activeOnly = false OR variant_status = 'ACTIVE')
                ORDER BY product_id ASC, sku ASC, variant_id ASC
                """, new MapSqlParameterSource()
                .addValue("productIds", productIds)
                .addValue("activeOnly", activeOnly), (rs, row) -> variant(rs));
    }

    public Optional<ProductVariant> findVariant(UUID variantId) {
        return jdbc.query("""
                SELECT variant_id, product_id, sku, size, color, material, unit_price_vnd, variant_status
                FROM public.product_variant
                WHERE variant_id = :variantId
                """, new MapSqlParameterSource("variantId", variantId), rs ->
                rs.next() ? Optional.of(variant(rs)) : Optional.empty());
    }

    public List<ProductVariant> activeVariants(Collection<UUID> variantIds) {
        if (variantIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT v.variant_id, v.product_id, v.sku, v.size, v.color, v.material,
                       v.unit_price_vnd, v.variant_status
                FROM public.product_variant v
                JOIN public.product p ON p.product_id = v.product_id AND p.product_status = 'ACTIVE'
                JOIN public.category c ON c.category_id = p.category_id AND c.is_active
                JOIN public.brand b ON b.brand_id = p.brand_id AND b.is_active
                WHERE v.variant_id IN (:variantIds) AND v.variant_status = 'ACTIVE'
                ORDER BY v.variant_id ASC
                """, new MapSqlParameterSource("variantIds", variantIds), (rs, row) -> variant(rs));
    }

    public VariantImage insertImage(UUID imageId, UUID variantId, String imageUrl, String altText,
            int displayOrder, boolean primary) {
        jdbc.update("""
                INSERT INTO public.variant_image (
                    image_id, variant_id, image_url, alt_text, display_order, is_primary
                ) VALUES (:imageId, :variantId, :imageUrl, :altText, :displayOrder, :primary)
                """, new MapSqlParameterSource()
                .addValue("imageId", imageId)
                .addValue("variantId", variantId)
                .addValue("imageUrl", imageUrl, Types.VARCHAR)
                .addValue("altText", altText, Types.VARCHAR)
                .addValue("displayOrder", displayOrder, Types.INTEGER)
                .addValue("primary", primary, Types.BOOLEAN));
        return findImage(imageId).orElseThrow();
    }

    public int nextImageDisplayOrder(UUID variantId) {
        Integer value = jdbc.queryForObject("""
                SELECT COALESCE(max(display_order) + 1, 0)
                FROM public.variant_image WHERE variant_id = :variantId
                """, new MapSqlParameterSource("variantId", variantId), Integer.class);
        return value == null ? 0 : value;
    }

    public boolean variantHasImages(UUID variantId) {
        return exists("SELECT EXISTS (SELECT 1 FROM public.variant_image WHERE variant_id = :id)", variantId);
    }

    public Optional<VariantImage> findImage(UUID imageId) {
        return jdbc.query("""
                SELECT image_id, variant_id, image_url, alt_text, display_order, is_primary
                FROM public.variant_image WHERE image_id = :imageId
                """, new MapSqlParameterSource("imageId", imageId), rs ->
                rs.next() ? Optional.of(image(rs)) : Optional.empty());
    }

    public List<VariantImage> imagesForVariants(Collection<UUID> variantIds) {
        if (variantIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT image_id, variant_id, image_url, alt_text, display_order, is_primary
                FROM public.variant_image
                WHERE variant_id IN (:variantIds)
                ORDER BY variant_id ASC, display_order ASC, image_id ASC
                """, new MapSqlParameterSource("variantIds", variantIds), (rs, row) -> image(rs));
    }

    public void reorderImages(UUID variantId, Map<UUID, Integer> order, UUID primaryImageId) {
        jdbc.update("UPDATE public.variant_image SET is_primary = false WHERE variant_id = :variantId",
                new MapSqlParameterSource("variantId", variantId));
        int temporary = 100_000;
        for (UUID imageId : order.keySet()) {
            jdbc.update("""
                    UPDATE public.variant_image SET display_order = :displayOrder
                    WHERE variant_id = :variantId AND image_id = :imageId
                    """, new MapSqlParameterSource()
                    .addValue("variantId", variantId)
                    .addValue("imageId", imageId)
                    .addValue("displayOrder", temporary++));
        }
        for (Map.Entry<UUID, Integer> entry : order.entrySet()) {
            jdbc.update("""
                    UPDATE public.variant_image SET display_order = :displayOrder
                    WHERE variant_id = :variantId AND image_id = :imageId
                    """, new MapSqlParameterSource()
                    .addValue("variantId", variantId)
                    .addValue("imageId", entry.getKey())
                    .addValue("displayOrder", entry.getValue()));
        }
        if (primaryImageId != null) {
            jdbc.update("""
                    UPDATE public.variant_image SET is_primary = true
                    WHERE variant_id = :variantId AND image_id = :imageId
                    """, new MapSqlParameterSource()
                    .addValue("variantId", variantId)
                    .addValue("imageId", primaryImageId));
        }
    }

    public int deleteImage(UUID imageId) {
        return jdbc.update("DELETE FROM public.variant_image WHERE image_id = :imageId",
                new MapSqlParameterSource("imageId", imageId));
    }

    private boolean exists(String sql, UUID id) {
        Boolean exists = jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    private MapSqlParameterSource productParameters(UUID productId, ProductCommand command) {
        return new MapSqlParameterSource()
                .addValue("productId", productId)
                .addValue("categoryId", command.categoryId())
                .addValue("brandId", command.brandId())
                .addValue("name", command.name(), Types.VARCHAR)
                .addValue("description", command.description(), Types.VARCHAR)
                .addValue("demographic", command.demographicSegment(), Types.VARCHAR)
                .addValue("status", command.productStatus().name(), Types.VARCHAR);
    }

    private MapSqlParameterSource variantParameters(UUID productId, UUID variantId, VariantCommand command) {
        return new MapSqlParameterSource()
                .addValue("productId", productId)
                .addValue("variantId", variantId)
                .addValue("sku", command.sku(), Types.VARCHAR)
                .addValue("size", command.size(), Types.VARCHAR)
                .addValue("color", command.color(), Types.VARCHAR)
                .addValue("material", command.material(), Types.VARCHAR)
                .addValue("price", command.unitPriceVnd(), Types.BIGINT)
                .addValue("status", command.variantStatus().name(), Types.VARCHAR);
    }

    private MapSqlParameterSource listParameters(ProductListCriteria criteria) {
        String keyword = criteria.keyword() == null ? null
                : "%" + criteria.keyword().toLowerCase(java.util.Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return new MapSqlParameterSource()
                .addValue("keyword", keyword, Types.VARCHAR)
                .addValue("categoryId", criteria.categoryId())
                .addValue("minPrice", criteria.minPriceVnd(), Types.BIGINT)
                .addValue("maxPrice", criteria.maxPriceVnd(), Types.BIGINT)
                .addValue("demographic", criteria.demographicSegment(), Types.VARCHAR)
                .addValue("size", criteria.size(), Types.INTEGER)
                .addValue("offset", criteria.offset(), Types.BIGINT);
    }

    private Product product(ResultSet rs) throws SQLException {
        return new Product(rs.getObject("product_id", UUID.class), rs.getObject("category_id", UUID.class),
                rs.getObject("brand_id", UUID.class), rs.getString("category_name"), rs.getString("brand_name"),
                rs.getString("name"), rs.getString("description"), rs.getString("demographic_segment"),
                ProductStatus.valueOf(rs.getString("product_status")));
    }

    private ProductVariant variant(ResultSet rs) throws SQLException {
        return new ProductVariant(rs.getObject("variant_id", UUID.class), rs.getObject("product_id", UUID.class),
                rs.getString("sku"), rs.getString("size"), rs.getString("color"), rs.getString("material"),
                rs.getLong("unit_price_vnd"), ProductVariantStatus.valueOf(rs.getString("variant_status")));
    }

    private VariantImage image(ResultSet rs) throws SQLException {
        return new VariantImage(rs.getObject("image_id", UUID.class), rs.getObject("variant_id", UUID.class),
                rs.getString("image_url"), rs.getString("alt_text"), rs.getInt("display_order"),
                rs.getBoolean("is_primary"));
    }
}
