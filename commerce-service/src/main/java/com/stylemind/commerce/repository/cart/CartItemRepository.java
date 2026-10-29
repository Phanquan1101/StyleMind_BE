package com.stylemind.commerce.repository.cart;

import com.stylemind.commerce.entity.CartItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {
    Optional<CartItem> findByCartIdAndVariantId(UUID cartId, UUID variantId);
    List<CartItem> findByCartIdOrderByAddedAtAscCartItemIdAsc(UUID cartId);
    void deleteByCartId(UUID cartId);
    @Query(value = """
            select ci.cart_item_id, ci.cart_id, ci.variant_id, v.sku, p.name, b.name,
                   v.color, v.size, v.material, ci.quantity, v.unit_price_vnd,
                   cast(ci.quantity as bigint) * v.unit_price_vnd
            from public.cart_item ci join public.product_variant v on v.variant_id = ci.variant_id
            join public.product p on p.product_id = v.product_id join public.brand b on b.brand_id = p.brand_id
            where ci.cart_id = :cartId order by ci.added_at asc, ci.cart_item_id asc
            """, nativeQuery = true)
    List<Object[]> findItemRows(@Param("cartId") UUID cartId);
}
