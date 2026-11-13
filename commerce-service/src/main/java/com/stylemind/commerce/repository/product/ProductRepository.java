package com.stylemind.commerce.repository.product;

import com.stylemind.commerce.entity.Product;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    @Query("select p.categoryId, count(p) from Product p group by p.categoryId")
    java.util.List<Object[]> countProductsByCategory();

    long countByCategoryId(UUID categoryId);
    long countByBrandId(UUID brandId);

    boolean existsByCategoryIdAndProductStatus(UUID categoryId, String productStatus);
    boolean existsByBrandIdAndProductStatus(UUID brandId, String productStatus);

    @Query(value = """
            select p from Product p
            where p.productStatus = 'ACTIVE'
              and exists (select c.categoryId from Category c where c.categoryId = p.categoryId and c.active = true)
              and exists (select b.brandId from Brand b where b.brandId = p.brandId and b.active = true)
              and exists (select v.variantId from ProductVariant v where v.productId = p.productId
                    and v.variantStatus = 'ACTIVE'
                    and (:minPrice is null or v.unitPriceVnd >= :minPrice)
                    and (:maxPrice is null or v.unitPriceVnd <= :maxPrice))
              and (:keyword is null or lower(p.name) like :keyword or lower(coalesce(p.description, '')) like :keyword)
              and (:categoryCount = 0 or p.categoryId in :categoryIds)
              and (:demographic is null or p.demographicSegment = :demographic)
            """,
            countQuery = """
            select count(p) from Product p
            where p.productStatus = 'ACTIVE'
              and exists (select c.categoryId from Category c where c.categoryId = p.categoryId and c.active = true)
              and exists (select b.brandId from Brand b where b.brandId = p.brandId and b.active = true)
              and exists (select v.variantId from ProductVariant v where v.productId = p.productId
                    and v.variantStatus = 'ACTIVE'
                    and (:minPrice is null or v.unitPriceVnd >= :minPrice)
                    and (:maxPrice is null or v.unitPriceVnd <= :maxPrice))
              and (:keyword is null or lower(p.name) like :keyword or lower(coalesce(p.description, '')) like :keyword)
              and (:categoryCount = 0 or p.categoryId in :categoryIds)
              and (:demographic is null or p.demographicSegment = :demographic)
            """)
    Page<Product> searchPublic(@Param("keyword") String keyword, @Param("categoryCount") int categoryCount,
            @Param("categoryIds") java.util.Collection<UUID> categoryIds,
            @Param("demographic") String demographic, @Param("minPrice") Long minPrice,
            @Param("maxPrice") Long maxPrice, Pageable pageable);

    @Query(value = """
            select p.* from public.product p
            where p.product_status = 'ACTIVE'
              and exists (select 1 from public.category c where c.category_id = p.category_id and c.is_active = true)
              and exists (select 1 from public.brand b where b.brand_id = p.brand_id and b.is_active = true)
              and exists (select 1 from public.product_variant v where v.product_id = p.product_id
                    and v.variant_status = 'ACTIVE'
                    and (:minPrice is null or v.unit_price_vnd >= :minPrice)
                    and (:maxPrice is null or v.unit_price_vnd <= :maxPrice))
              and (:keyword is null or lower(p.name) like :keyword or lower(coalesce(p.description, '')) like :keyword)
              and (:categoryCount = 0 or p.category_id in (:categoryIds))
              and (:demographic is null or p.demographic_segment = :demographic)
            order by
              case when :sort = 'price_asc' then (select min(pv.unit_price_vnd) from public.product_variant pv
                    where pv.product_id = p.product_id and pv.variant_status = 'ACTIVE') end asc,
              case when :sort = 'price_desc' then (select min(pv.unit_price_vnd) from public.product_variant pv
                    where pv.product_id = p.product_id and pv.variant_status = 'ACTIVE') end desc,
              p.name asc, p.product_id asc
            """,
            countQuery = """
            select count(*) from public.product p
            where p.product_status = 'ACTIVE'
              and exists (select 1 from public.category c where c.category_id = p.category_id and c.is_active = true)
              and exists (select 1 from public.brand b where b.brand_id = p.brand_id and b.is_active = true)
              and exists (select 1 from public.product_variant v where v.product_id = p.product_id
                    and v.variant_status = 'ACTIVE'
                    and (:minPrice is null or v.unit_price_vnd >= :minPrice)
                    and (:maxPrice is null or v.unit_price_vnd <= :maxPrice))
              and (:keyword is null or lower(p.name) like :keyword or lower(coalesce(p.description, '')) like :keyword)
              and (:categoryCount = 0 or p.category_id in (:categoryIds))
              and (:demographic is null or p.demographic_segment = :demographic)
            """, nativeQuery = true)
    Page<Product> searchPublicByPrice(@Param("keyword") String keyword,
            @Param("categoryCount") int categoryCount,
            @Param("categoryIds") java.util.Collection<UUID> categoryIds,
            @Param("demographic") String demographic,
            @Param("minPrice") Long minPrice,
            @Param("maxPrice") Long maxPrice,
            @Param("sort") String sort,
            Pageable pageable);

    @Query(value = """
            select p from Product p
            where (:status is null or p.productStatus = :status)
              and (:categoryCount = 0 or p.categoryId in :categoryIds)
              and (:keyword is null or lower(p.name) like :keyword
                   or exists (select v.variantId from ProductVariant v where v.productId = p.productId and lower(v.sku) like :keyword))
            """,
            countQuery = """
            select count(p) from Product p
            where (:status is null or p.productStatus = :status)
              and (:categoryCount = 0 or p.categoryId in :categoryIds)
              and (:keyword is null or lower(p.name) like :keyword
                   or exists (select v.variantId from ProductVariant v where v.productId = p.productId and lower(v.sku) like :keyword))
            """)
    Page<Product> searchAdmin(@Param("keyword") String keyword, @Param("categoryCount") int categoryCount,
            @Param("categoryIds") java.util.Collection<UUID> categoryIds, @Param("status") String status,
            Pageable pageable);
}
