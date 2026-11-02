package com.stylemind.commerce.repository.cart;

import com.stylemind.commerce.entity.Cart;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartRepository extends JpaRepository<Cart, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<Cart> findLockedByCartId(UUID cartId);
    Optional<Cart> findByCustomerIdAndCartStatus(UUID customerId, String cartStatus);
    Optional<Cart> findByGuestSessionKeyAndCartStatus(String guestSessionKey, String cartStatus);
    @Query(value = """
            select v.variant_id, v.sku, p.name, b.name, v.color, v.size, v.material, v.unit_price_vnd,
                   (select i.image_url from public.variant_image i where i.variant_id = v.variant_id
                    order by i.is_primary desc, i.display_order asc, i.image_id asc limit 1)
            from public.product_variant v join public.product p on p.product_id = v.product_id and p.product_status = 'ACTIVE'
            join public.category c on c.category_id = p.category_id and c.is_active
            join public.brand b on b.brand_id = p.brand_id and b.is_active
            where v.variant_id in (:variantIds) and v.variant_status = 'ACTIVE'
            """, nativeQuery = true)
    List<Object[]> findActiveVariantRows(@Param("variantIds") Collection<UUID> variantIds);
}
