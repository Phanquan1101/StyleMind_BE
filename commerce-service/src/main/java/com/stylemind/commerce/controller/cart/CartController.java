package com.stylemind.commerce.controller.cart;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.commerce.dto.cart.CartResponse;
import com.stylemind.commerce.security.CurrentUser;
import com.stylemind.commerce.service.cart.CartService;
import com.stylemind.commerce.validation.cart.CartRequestValidator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CartController {

    private final CartService service;
    private final CartRequestValidator validator;

    @GetMapping(path = "/api/v1/cart", produces = MediaType.APPLICATION_JSON_VALUE)
    public CartResponse getCart(Authentication authentication, HttpServletRequest request) {
        return service.getCart(validator.ownerFromQuery(CurrentUser.subject(authentication), request));
    }

    @PostMapping(path = "/api/v1/cart/items", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public CartResponse addItem(Authentication authentication, @RequestBody JsonNode body) {
        var command = validator.itemCommand(body);
        return service.addItem(validator.owner(CurrentUser.subject(authentication), command.guestSessionKey()), command);
    }

    @PutMapping(path = "/api/v1/cart/items/{cartItemId}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public CartResponse updateItem(Authentication authentication, @PathVariable String cartItemId,
            @RequestBody JsonNode body) {
        var command = validator.updateCommand(body);
        return service.updateItem(validator.owner(CurrentUser.subject(authentication), command.guestSessionKey()),
                validator.pathId(cartItemId), command);
    }

    @DeleteMapping("/api/v1/cart/items/{cartItemId}")
    public ResponseEntity<Void> removeItem(Authentication authentication, @PathVariable String cartItemId,
            HttpServletRequest request) {
        service.removeItem(validator.ownerFromQuery(CurrentUser.subject(authentication), request),
                validator.pathId(cartItemId));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping(path = "/api/v1/cart/items", produces = MediaType.APPLICATION_JSON_VALUE)
    public CartResponse clear(Authentication authentication, HttpServletRequest request) {
        return service.clear(validator.ownerFromQuery(CurrentUser.subject(authentication), request));
    }

    @PostMapping(path = "/api/v1/cart/merge", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public CartResponse merge(Authentication authentication, @RequestBody JsonNode body) {
        java.util.UUID customerId = CurrentUser.subject(authentication);
        if (customerId == null) {
            throw new com.stylemind.commerce.errors.ApiOperationException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                    "Authentication is required");
        }
        return service.merge(customerId, validator.mergeGuestKey(body));
    }
}
