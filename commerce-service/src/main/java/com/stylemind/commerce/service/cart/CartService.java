package com.stylemind.commerce.service.cart;

import com.stylemind.commerce.dto.cart.CartItemCommand;
import com.stylemind.commerce.dto.cart.CartOwner;
import com.stylemind.commerce.dto.cart.CartResponse;
import java.util.UUID;

public interface CartService {

    CartResponse getCart(CartOwner owner);

    CartResponse addItem(CartOwner owner, CartItemCommand command);

    CartResponse updateItem(CartOwner owner, UUID cartItemId, CartItemCommand command);

    void removeItem(CartOwner owner, UUID cartItemId);

    CartResponse clear(CartOwner owner);

    CartResponse merge(UUID customerId, String guestSessionKey);
}
