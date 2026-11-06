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
              and (:categoryId is null or p.categoryId = :categoryId)
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
              and (:categoryId is null or p.categoryId = :categoryId)
              and (:demographic is null or p.demographicSegment = :demographic)
            """)
    Page<Product> searchPublic(@Param("keyword") String keyword, @Param("categoryId") UUID categoryId,
            @Param("demographic") String demographic, @Param("minPrice") Long minPrice,
            @Param("maxPrice") Long maxPrice, Pageable pageable);
}
