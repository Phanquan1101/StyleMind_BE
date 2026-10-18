package com.stylemind.commerce.repository.cart;

import com.stylemind.commerce.model.cart.Cart;
import com.stylemind.commerce.model.cart.CartItem;
import com.stylemind.commerce.model.cart.CartOwner;
import com.stylemind.commerce.model.cart.VariantPurchaseInfo;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CartRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public UUID findOrCreateActiveCart(CartOwner owner) {
        return findActiveCartId(owner).orElseGet(() -> {
            UUID cartId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO public.cart (cart_id, customer_id, guest_session_key, cart_status, created_at, updated_at)
                    VALUES (:cartId, :customerId, :guestSessionKey, 'ACTIVE', :now, :now)
                    """, ownerParams(owner).addValue("cartId", cartId).addValue("now", OffsetDateTime.now()));
            return cartId;
        });
    }

    public Optional<UUID> findActiveCartId(CartOwner owner) {
        String predicate = owner.customer() ? "customer_id = :customerId" : "guest_session_key = :guestSessionKey";
        return jdbc.query("SELECT cart_id FROM public.cart WHERE cart_status = 'ACTIVE' AND " + predicate,
                ownerParams(owner), rs -> rs.next() ? Optional.of(rs.getObject("cart_id", UUID.class)) : Optional.empty());
    }

    public Optional<CartOwner> ownerOfItem(UUID cartItemId) {
        return jdbc.query("""
                SELECT c.customer_id, c.guest_session_key
                FROM public.cart_item ci JOIN public.cart c ON c.cart_id = ci.cart_id
                WHERE ci.cart_item_id = :cartItemId AND c.cart_status = 'ACTIVE'
                """, new MapSqlParameterSource("cartItemId", cartItemId), rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    UUID customerId = rs.getObject("customer_id", UUID.class);
                    return Optional.of(customerId == null
                            ? CartOwner.guest(rs.getString("guest_session_key")) : CartOwner.customer(customerId));
                });
    }

    public void upsertItem(UUID cartId, UUID variantId, int quantity, long price, boolean addQuantity) {
        jdbc.update("""
                INSERT INTO public.cart_item (
                    cart_item_id, cart_id, variant_id, quantity, last_seen_price_vnd, added_at, updated_at
                ) VALUES (:itemId, :cartId, :variantId, :quantity, :price, :now, :now)
                ON CONFLICT (cart_id, variant_id) DO UPDATE
                SET quantity = CASE WHEN :addQuantity THEN public.cart_item.quantity + EXCLUDED.quantity
                                    ELSE EXCLUDED.quantity END,
                    last_seen_price_vnd = EXCLUDED.last_seen_price_vnd,
                    updated_at = EXCLUDED.updated_at
                """, new MapSqlParameterSource()
                .addValue("itemId", UUID.randomUUID())
                .addValue("cartId", cartId)
                .addValue("variantId", variantId)
                .addValue("quantity", quantity)
                .addValue("price", price)
                .addValue("addQuantity", addQuantity)
                .addValue("now", OffsetDateTime.now()));
        touch(cartId);
    }

    public int updateItem(UUID cartItemId, int quantity, long price) {
        int rows = jdbc.update("""
                UPDATE public.cart_item SET quantity = :quantity, last_seen_price_vnd = :price, updated_at = :now
                WHERE cart_item_id = :cartItemId
                """, new MapSqlParameterSource()
                .addValue("cartItemId", cartItemId)
                .addValue("quantity", quantity)
                .addValue("price", price)
                .addValue("now", OffsetDateTime.now()));
        if (rows == 1) {
            jdbc.update("""
                    UPDATE public.cart SET updated_at = :now
                    WHERE cart_id = (SELECT cart_id FROM public.cart_item WHERE cart_item_id = :cartItemId)
                    """, new MapSqlParameterSource("cartItemId", cartItemId).addValue("now", OffsetDateTime.now()));
        }
        return rows;
    }

    public int deleteItem(UUID cartItemId) {
        return jdbc.update("DELETE FROM public.cart_item WHERE cart_item_id = :cartItemId",
                new MapSqlParameterSource("cartItemId", cartItemId));
    }

    public void clearCart(UUID cartId) {
        jdbc.update("DELETE FROM public.cart_item WHERE cart_id = :cartId", new MapSqlParameterSource("cartId", cartId));
        touch(cartId);
    }

    public List<CartItem> items(UUID cartId) {
        return jdbc.query("""
                SELECT ci.cart_item_id, ci.cart_id, ci.variant_id, v.sku, p.name AS product_name, b.name AS brand_name,
                       v.color, v.size, v.material, ci.quantity, v.unit_price_vnd,
                       ci.quantity::bigint * v.unit_price_vnd AS line_total_vnd
                FROM public.cart_item ci
                JOIN public.product_variant v ON v.variant_id = ci.variant_id
                JOIN public.product p ON p.product_id = v.product_id
                JOIN public.brand b ON b.brand_id = p.brand_id
                WHERE ci.cart_id = :cartId
                ORDER BY ci.added_at ASC, ci.cart_item_id ASC
                """, new MapSqlParameterSource("cartId", cartId), (rs, row) -> item(rs));
    }

    public Cart cart(UUID cartId) {
        return jdbc.query("""
                SELECT cart_id, customer_id, guest_session_key, cart_status FROM public.cart WHERE cart_id = :cartId
                """, new MapSqlParameterSource("cartId", cartId), rs -> {
                    if (!rs.next()) {
                        throw new IllegalStateException("cart disappeared");
                    }
                    return new Cart(rs.getObject("cart_id", UUID.class), rs.getObject("customer_id", UUID.class),
                            rs.getString("guest_session_key"), rs.getString("cart_status"), items(cartId));
                });
    }

    public List<VariantPurchaseInfo> activeVariantInfo(Collection<UUID> variantIds) {
        if (variantIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT v.variant_id, v.sku, p.name AS product_name, b.name AS brand_name,
                       v.color, v.size, v.material, v.unit_price_vnd,
                       (SELECT image_url FROM public.variant_image i
                        WHERE i.variant_id = v.variant_id
                        ORDER BY i.is_primary DESC, i.display_order ASC, i.image_id ASC LIMIT 1) AS image_url
                FROM public.product_variant v
                JOIN public.product p ON p.product_id = v.product_id AND p.product_status = 'ACTIVE'
                JOIN public.category c ON c.category_id = p.category_id AND c.is_active
                JOIN public.brand b ON b.brand_id = p.brand_id AND b.is_active
                WHERE v.variant_id IN (:variantIds) AND v.variant_status = 'ACTIVE'
                """, new MapSqlParameterSource("variantIds", variantIds), (rs, row) -> variantInfo(rs));
    }

    public void markMerged(UUID guestCartId) {
        jdbc.update("""
                UPDATE public.cart SET cart_status = 'MERGED', merged_at = :now, updated_at = :now
                WHERE cart_id = :cartId AND cart_status = 'ACTIVE'
                """, new MapSqlParameterSource("cartId", guestCartId).addValue("now", OffsetDateTime.now()));
    }

    private void touch(UUID cartId) {
        jdbc.update("UPDATE public.cart SET updated_at = :now WHERE cart_id = :cartId",
                new MapSqlParameterSource("cartId", cartId).addValue("now", OffsetDateTime.now()));
    }

    private MapSqlParameterSource ownerParams(CartOwner owner) {
        return new MapSqlParameterSource()
                .addValue("customerId", owner.customerId())
                .addValue("guestSessionKey", owner.guestSessionKey(), Types.VARCHAR);
    }

    private CartItem item(ResultSet rs) throws SQLException {
        return new CartItem(rs.getObject("cart_item_id", UUID.class), rs.getObject("cart_id", UUID.class),
                rs.getObject("variant_id", UUID.class), rs.getString("sku"), rs.getString("product_name"),
                rs.getString("brand_name"), rs.getString("color"), rs.getString("size"), rs.getString("material"),
                rs.getInt("quantity"), rs.getLong("unit_price_vnd"), rs.getLong("line_total_vnd"));
    }

    private VariantPurchaseInfo variantInfo(ResultSet rs) throws SQLException {
        return new VariantPurchaseInfo(rs.getObject("variant_id", UUID.class), rs.getString("sku"),
                rs.getString("product_name"), rs.getString("brand_name"), rs.getString("color"),
                rs.getString("size"), rs.getString("material"), rs.getLong("unit_price_vnd"),
                rs.getString("image_url"));
    }
}
