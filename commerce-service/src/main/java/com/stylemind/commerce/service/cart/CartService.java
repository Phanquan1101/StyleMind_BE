package com.stylemind.commerce.service.cart;

import com.stylemind.commerce.dto.cart.CartItemResponse;
import com.stylemind.commerce.dto.cart.CartResponse;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.model.cart.Cart;
import com.stylemind.commerce.model.cart.CartItem;
import com.stylemind.commerce.model.cart.CartItemCommand;
import com.stylemind.commerce.model.cart.CartOwner;
import com.stylemind.commerce.model.cart.VariantPurchaseInfo;
import com.stylemind.commerce.repository.cart.CartRepository;
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

    private final CartRepository repository;

    @Transactional(readOnly = true)
    public CartResponse getCart(CartOwner owner) {
        UUID cartId = repository.findActiveCartId(owner).orElse(null);
        if (cartId == null) {
            return new CartResponse(null, owner.customerId(), owner.guestSessionKey(), "ACTIVE", List.of(), 0);
        }
        return response(repository.cart(cartId));
    }

    @Transactional
    public CartResponse addItem(CartOwner owner, CartItemCommand command) {
        VariantPurchaseInfo variant = activeVariant(command.variantId());
        UUID cartId = repository.findOrCreateActiveCart(owner);
        repository.upsertItem(cartId, variant.variantId(), command.quantity(), variant.unitPriceVnd(), true);
        return response(repository.cart(cartId));
    }

    @Transactional
    public CartResponse updateItem(CartOwner owner, UUID cartItemId, CartItemCommand command) {
        requireItemOwner(owner, cartItemId);
        UUID cartId = repository.findActiveCartId(owner).orElseThrow(this::notFound);
        CartItem existing = repository.items(cartId).stream()
                .filter(item -> item.cartItemId().equals(cartItemId)).findFirst().orElseThrow(this::notFound);
        VariantPurchaseInfo variant = activeVariant(existing.variantId());
        if (repository.updateItem(cartItemId, command.quantity(), variant.unitPriceVnd()) != 1) {
            throw notFound();
        }
        return response(repository.cart(cartId));
    }

    @Transactional
    public void removeItem(CartOwner owner, UUID cartItemId) {
        requireItemOwner(owner, cartItemId);
        if (repository.deleteItem(cartItemId) != 1) {
            throw notFound();
        }
    }

    @Transactional
    public CartResponse clear(CartOwner owner) {
        UUID cartId = repository.findOrCreateActiveCart(owner);
        repository.clearCart(cartId);
        return response(repository.cart(cartId));
    }

    @Transactional
    public CartResponse merge(UUID customerId, String guestSessionKey) {
        CartOwner guest = CartOwner.guest(guestSessionKey);
        UUID guestCartId = repository.findActiveCartId(guest).orElse(null);
        UUID customerCartId = repository.findOrCreateActiveCart(CartOwner.customer(customerId));
        if (guestCartId == null || guestCartId.equals(customerCartId)) {
            return response(repository.cart(customerCartId));
        }
        List<CartItem> guestItems = repository.items(guestCartId);
        Map<UUID, VariantPurchaseInfo> active = repository.activeVariantInfo(
                guestItems.stream().map(CartItem::variantId).toList()).stream()
                .collect(Collectors.toMap(VariantPurchaseInfo::variantId, item -> item));
        if (active.size() != guestItems.size()) {
            throw new ApiOperationException(HttpStatus.CONFLICT, "INVALID_CART_ITEM",
                    "Guest cart contains an inactive or unavailable variant");
        }
        for (CartItem item : guestItems) {
            VariantPurchaseInfo variant = active.get(item.variantId());
            repository.upsertItem(customerCartId, item.variantId(), item.quantity(), variant.unitPriceVnd(), true);
        }
        repository.markMerged(guestCartId);
        return response(repository.cart(customerCartId));
    }

    private void requireItemOwner(CartOwner owner, UUID cartItemId) {
        CartOwner actual = repository.ownerOfItem(cartItemId).orElseThrow(this::notFound);
        boolean match = owner.customer()
                ? owner.customerId().equals(actual.customerId())
                : owner.guestSessionKey().equals(actual.guestSessionKey());
        if (!match) {
            throw notFound();
        }
    }

    private VariantPurchaseInfo activeVariant(UUID variantId) {
        List<VariantPurchaseInfo> variants = repository.activeVariantInfo(List.of(variantId));
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

    private CartItemResponse itemResponse(CartItem item) {
        return new CartItemResponse(item.cartItemId(), item.variantId(), item.sku(), item.productName(),
                item.brandName(), item.color(), item.size(), item.material(), item.quantity(),
                item.unitPriceVnd(), item.lineTotalVnd());
    }

    private ApiOperationException notFound() {
        return new ApiOperationException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found");
    }
}
