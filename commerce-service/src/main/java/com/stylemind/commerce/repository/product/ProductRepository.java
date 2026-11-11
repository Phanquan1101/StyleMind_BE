package com.stylemind.commerce.repository.product;

import com.stylemind.commerce.entity.Product;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {
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
