package com.stylemind.commerce.repository.product;

import com.stylemind.commerce.entity.VariantImage;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VariantImageRepository extends JpaRepository<VariantImage, UUID> {
    List<VariantImage> findByVariantIdInOrderByVariantIdAscDisplayOrderAscImageIdAsc(Collection<UUID> variantIds);
    int countByVariantId(UUID variantId);
    java.util.Optional<VariantImage> findTopByVariantIdOrderByDisplayOrderDesc(UUID variantId);
}
