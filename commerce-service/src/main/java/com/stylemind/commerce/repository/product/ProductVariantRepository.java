package com.stylemind.commerce.repository.product;

import com.stylemind.commerce.entity.ProductVariant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {
    List<ProductVariant> findByProductIdInOrderByProductIdAscSkuAscVariantIdAsc(Collection<UUID> productIds);
    List<ProductVariant> findByVariantIdInOrderByVariantIdAsc(Collection<UUID> variantIds);
}
