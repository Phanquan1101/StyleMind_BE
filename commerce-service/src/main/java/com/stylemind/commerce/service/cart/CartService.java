package com.stylemind.commerce.service.cart;

import com.stylemind.commerce.dto.cart.CartItemResponse;
import com.stylemind.commerce.dto.cart.CartResponse;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.dto.cart.Cart;
import com.stylemind.commerce.dto.cart.CartItem;
import com.stylemind.commerce.dto.cart.CartItemCommand;
import com.stylemind.commerce.dto.cart.CartOwner;
import com.stylemind.commerce.dto.cart.VariantPurchaseInfo;
import com.stylemind.commerce.repository.cart.CartRepository;
import com.stylemind.commerce.repository.cart.CartItemRepository;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository carts;
    private final CartItemRepository cartItems;

    @Transactional(readOnly = true)
    public CartResponse getCart(CartOwner owner) {
        UUID cartId = findActiveCartId(owner).orElse(null);
        if (cartId == null) {
            return new CartResponse(null, owner.customerId(), owner.guestSessionKey(), "ACTIVE", List.of(), 0);
        }
        return response(cart(cartId));
    }

    @Transactional
    public CartResponse addItem(CartOwner owner, CartItemCommand command) {
        VariantPurchaseInfo variant = activeVariant(command.variantId());
        UUID cartId = findOrCreateActiveCart(owner);
        upsertItem(cartId, variant.variantId(), command.quantity(), variant.unitPriceVnd(), true);
        return response(cart(cartId));
    }

    @Transactional
    public CartResponse updateItem(CartOwner owner, UUID cartItemId, CartItemCommand command) {
        requireItemOwner(owner, cartItemId);
        UUID cartId = findActiveCartId(owner).orElseThrow(this::notFound);
        CartItem existing = items(cartId).stream()
                .filter(item -> item.cartItemId().equals(cartItemId)).findFirst().orElseThrow(this::notFound);
        VariantPurchaseInfo variant = activeVariant(existing.variantId());
        if (updateItem(cartItemId, command.quantity(), variant.unitPriceVnd()) != 1) {
            throw notFound();
        }
        return response(cart(cartId));
    }

    @Transactional
    public void removeItem(CartOwner owner, UUID cartItemId) {
        requireItemOwner(owner, cartItemId);
        if (deleteItem(cartItemId) != 1) {
            throw notFound();
        }
    }

    @Transactional
    public CartResponse clear(CartOwner owner) {
        UUID cartId = findOrCreateActiveCart(owner);
        clearCart(cartId);
        return response(cart(cartId));
    }

    @Transactional
    public CartResponse merge(UUID customerId, String guestSessionKey) {
        CartOwner guest = CartOwner.guest(guestSessionKey);
        UUID guestCartId = findActiveCartId(guest).orElse(null);
        UUID customerCartId = findOrCreateActiveCart(CartOwner.customer(customerId));
        if (guestCartId == null || guestCartId.equals(customerCartId)) {
            return response(cart(customerCartId));
        }
        List<CartItem> guestItems = items(guestCartId);
        Map<UUID, VariantPurchaseInfo> active = activeVariantInfo(
                guestItems.stream().map(CartItem::variantId).toList()).stream()
                .collect(Collectors.toMap(VariantPurchaseInfo::variantId, item -> item));
        if (active.size() != guestItems.size()) {
            throw new ApiOperationException(HttpStatus.CONFLICT, "INVALID_CART_ITEM",
                    "Guest cart contains an inactive or unavailable variant");
        }
        for (CartItem item : guestItems) {
            VariantPurchaseInfo variant = active.get(item.variantId());
            upsertItem(customerCartId, item.variantId(), item.quantity(), variant.unitPriceVnd(), true);
        }
        markMerged(guestCartId);
        return response(cart(customerCartId));
    }

    private void requireItemOwner(CartOwner owner, UUID cartItemId) {
        CartOwner actual = ownerOfItem(cartItemId).orElseThrow(this::notFound);
        boolean match = owner.customer()
                ? owner.customerId().equals(actual.customerId())
                : owner.guestSessionKey().equals(actual.guestSessionKey());
        if (!match) {
            throw notFound();
        }
    }

    private VariantPurchaseInfo activeVariant(UUID variantId) {
        List<VariantPurchaseInfo> variants = activeVariantInfo(List.of(variantId));
        if (variants.isEmpty()) {
            throw new ApiOperationException(HttpStatus.CONFLICT, "VARIANT_NOT_ACTIVE",
                    "Variant does not exist or is inactive");
        }
        return variants.getFirst();
    }

    private CartResponse response(Cart cart) {
        return new CartResponse(cart.cartId(), cart.customerId(), cart.guestSessionKey(), cart.cartStatus(),
                cart.items().stream().map(this::itemResponse).toList(), cart.totalVnd());
    }

    private UUID findOrCreateActiveCart(CartOwner owner) {
        return findActiveCartId(owner).orElseGet(() -> {
            com.stylemind.commerce.entity.Cart entity = new com.stylemind.commerce.entity.Cart();
            UUID id = UUID.randomUUID(); entity.setCartId(id); entity.setCustomerId(owner.customerId());
            entity.setGuestSessionKey(owner.guestSessionKey()); entity.setCartStatus("ACTIVE");
            entity.setCreatedAt(OffsetDateTime.now()); entity.setUpdatedAt(OffsetDateTime.now()); carts.saveAndFlush(entity); return id;
        });
    }

    private java.util.Optional<UUID> findActiveCartId(CartOwner owner) {
        return (owner.customer() ? carts.findByCustomerIdAndCartStatus(owner.customerId(), "ACTIVE")
                : carts.findByGuestSessionKeyAndCartStatus(owner.guestSessionKey(), "ACTIVE"))
                .map(com.stylemind.commerce.entity.Cart::getCartId);
    }

    private java.util.Optional<CartOwner> ownerOfItem(UUID id) {
        return cartItems.findById(id).flatMap(item -> carts.findById(item.getCartId()))
                .filter(c -> "ACTIVE".equals(c.getCartStatus()))
                .map(c -> c.getCustomerId() == null ? CartOwner.guest(c.getGuestSessionKey()) : CartOwner.customer(c.getCustomerId()));
    }

    private void upsertItem(UUID cartId, UUID variantId, int quantity, long price, boolean add) {
        com.stylemind.commerce.entity.CartItem item = cartItems.findByCartIdAndVariantId(cartId, variantId).orElseGet(() -> {
            com.stylemind.commerce.entity.CartItem created = new com.stylemind.commerce.entity.CartItem(); created.setCartItemId(UUID.randomUUID());
            created.setCartId(cartId); created.setVariantId(variantId); created.setAddedAt(OffsetDateTime.now()); return created;
        });
        item.setQuantity(add && item.getQuantity() > 0 ? item.getQuantity() + quantity : quantity);
        item.setLastSeenPriceVnd(price); item.setUpdatedAt(OffsetDateTime.now()); cartItems.saveAndFlush(item); touch(cartId);
    }

    private int updateItem(UUID id, int quantity, long price) {
        var found = cartItems.findById(id); if (found.isEmpty()) return 0;
        var item = found.get(); item.setQuantity(quantity); item.setLastSeenPriceVnd(price); item.setUpdatedAt(OffsetDateTime.now()); cartItems.saveAndFlush(item); touch(item.getCartId()); return 1;
    }

    private int deleteItem(UUID id) { if (!cartItems.existsById(id)) return 0; cartItems.deleteById(id); return 1; }
    private void clearCart(UUID id) { cartItems.deleteByCartId(id); touch(id); }
    private void touch(UUID id) { carts.findById(id).ifPresent(c -> { c.setUpdatedAt(OffsetDateTime.now()); carts.saveAndFlush(c); }); }

    private Cart cart(UUID id) {
        var c = carts.findById(id).orElseThrow(() -> new IllegalStateException("cart disappeared"));
        List<CartItem> items = cartItems.findItemRows(id).stream().map(this::item).toList();
        return new Cart(c.getCartId(), c.getCustomerId(), c.getGuestSessionKey(), c.getCartStatus(), items);
    }

    private List<CartItem> items(UUID id) { return cartItems.findItemRows(id).stream().map(this::item).toList(); }

    private List<VariantPurchaseInfo> activeVariantInfo(Collection<UUID> ids) {
        return ids.isEmpty() ? List.of() : carts.findActiveVariantRows(ids).stream().map(this::variantInfo).toList();
    }

    private void markMerged(UUID id) { carts.findById(id).filter(c -> "ACTIVE".equals(c.getCartStatus())).ifPresent(c -> { c.setCartStatus("MERGED"); c.setMergedAt(OffsetDateTime.now()); c.setUpdatedAt(OffsetDateTime.now()); carts.saveAndFlush(c); }); }

    private CartItem item(Object[] row) { return new CartItem((UUID) row[0], (UUID) row[1], (UUID) row[2], (String) row[3], (String) row[4], (String) row[5], (String) row[6], (String) row[7], (String) row[8], ((Number) row[9]).intValue(), ((Number) row[10]).longValue(), ((Number) row[11]).longValue()); }
    private VariantPurchaseInfo variantInfo(Object[] row) { return new VariantPurchaseInfo((UUID) row[0], (String) row[1], (String) row[2], (String) row[3], (String) row[4], (String) row[5], (String) row[6], ((Number) row[7]).longValue(), (String) row[8]); }

    private CartItemResponse itemResponse(CartItem item) {
        return new CartItemResponse(item.cartItemId(), item.variantId(), item.sku(), item.productName(),
                item.brandName(), item.color(), item.size(), item.material(), item.quantity(),
                item.unitPriceVnd(), item.lineTotalVnd());
    }

    private ApiOperationException notFound() {
        return new ApiOperationException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found");
    }
}
