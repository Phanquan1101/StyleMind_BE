package com.stylemind.commerce.repository.product;

import com.stylemind.commerce.entity.ProductVariant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {
    List<ProductVariant> findByProductIdInOrderByProductIdAscSkuAscVariantIdAsc(Collection<UUID> productIds);
    List<ProductVariant> findByVariantIdInOrderByVariantIdAsc(Collection<UUID> variantIds);

    /** Rows for the internal batch lookup; purchasable mirrors the cart rule (variant, product, category, brand active). */
    @Query(value = """
            select v.variant_id, v.product_id, p.name, p.product_status, v.variant_status, v.color, v.size,
                   v.unit_price_vnd,
                   (select i.image_url from public.variant_image i where i.variant_id = v.variant_id
                    order by i.is_primary desc, i.display_order asc, i.image_id asc limit 1),
                   (v.variant_status = 'ACTIVE' and p.product_status = 'ACTIVE' and c.is_active and b.is_active)
            from public.product_variant v
            join public.product p on p.product_id = v.product_id
            join public.category c on c.category_id = p.category_id
            join public.brand b on b.brand_id = p.brand_id
            where v.variant_id in (:variantIds)
            order by v.variant_id
            """, nativeQuery = true)
    List<Object[]> findInternalLookupRows(@Param("variantIds") Collection<UUID> variantIds);
}
