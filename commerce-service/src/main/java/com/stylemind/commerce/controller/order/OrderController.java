package com.stylemind.commerce.controller.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.commerce.dto.order.CancellationRequestResponse;
import com.stylemind.commerce.dto.order.CheckoutPriceResponse;
import com.stylemind.commerce.dto.order.OrderListResponse;
import com.stylemind.commerce.dto.order.OrderResponse;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.security.CurrentUser;
import com.stylemind.commerce.service.order.OrderService;
import com.stylemind.commerce.validation.order.OrderRequestValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService service;
    private final OrderRequestValidator validator;

    @GetMapping(path = "/api/v1/checkout/prices", produces = MediaType.APPLICATION_JSON_VALUE)
    public CheckoutPriceResponse preview(Authentication authentication) {
        return service.preview(requireSubject(authentication));
    }

    @PostMapping(path = "/api/v1/orders", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderResponse> checkout(Authentication authentication, HttpServletRequest request,
            @RequestBody JsonNode body) {
        OrderResponse created = service.checkout(requireSubject(authentication),
                request.getHeader(HttpHeaders.AUTHORIZATION), validator.checkout(body));
        return ResponseEntity.created(URI.create("/api/v1/orders/" + created.orderId())).body(created);
    }

    @GetMapping(path = "/api/v1/orders", produces = MediaType.APPLICATION_JSON_VALUE)
    public OrderListResponse list(Authentication authentication, HttpServletRequest request) {
        return service.list(requireSubject(authentication), false, validator.list(request));
    }

    @GetMapping(path = "/api/v1/orders/{orderId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public OrderResponse get(Authentication authentication, @PathVariable String orderId) {
        return service.get(requireSubject(authentication), false, validator.pathId(orderId));
    }

    @PostMapping(path = "/api/v1/orders/{orderId}/cancellation-requests",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CancellationRequestResponse> requestCancellation(Authentication authentication,
            @PathVariable String orderId, @RequestBody JsonNode body) {
        CancellationRequestResponse created = service.requestCancellation(requireSubject(authentication),
                validator.pathId(orderId), validator.cancellationReason(body));
        return ResponseEntity.created(URI.create("/api/v1/admin/cancellation-requests/"
                + created.cancellationRequestId())).body(created);
    }

    @GetMapping(path = "/api/v1/admin/orders", produces = MediaType.APPLICATION_JSON_VALUE)
    public OrderListResponse adminList(Authentication authentication, HttpServletRequest request) {
        return service.list(requireSubject(authentication), true, validator.list(request));
    }

    @GetMapping(path = "/api/v1/admin/orders/{orderId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public OrderResponse adminGet(Authentication authentication, @PathVariable String orderId) {
        return service.get(requireSubject(authentication), true, validator.pathId(orderId));
    }

    @PutMapping(path = "/api/v1/admin/orders/{orderId}/status", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public OrderResponse updateStatus(Authentication authentication, @PathVariable String orderId,
            @RequestBody JsonNode body) {
        return service.updateStatus(requireSubject(authentication), validator.pathId(orderId), validator.status(body));
    }

    @PutMapping(path = "/api/v1/admin/cancellation-requests/{requestId}",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CancellationRequestResponse reviewCancellation(Authentication authentication, @PathVariable String requestId,
            @RequestBody JsonNode body) {
        return service.reviewCancellation(requireSubject(authentication), validator.pathId(requestId),
                validator.review(body));
    }

    private UUID requireSubject(Authentication authentication) {
        UUID subject = CurrentUser.subject(authentication);
        if (subject == null) {
            throw new ApiOperationException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                    "Authentication is required");
        }
        return subject;
    }
}
