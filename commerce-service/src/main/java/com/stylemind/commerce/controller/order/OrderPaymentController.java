package com.stylemind.commerce.controller.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.commerce.client.payment.OrderPaymentRequest;
import com.stylemind.commerce.client.payment.PaymentAttemptResponse;
import com.stylemind.commerce.client.payment.PaymentClient;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.security.CurrentUser;
import com.stylemind.commerce.service.order.OrderService;
import com.stylemind.commerce.validation.order.OrderRequestValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderPaymentController {
    private final OrderService orders;
    private final OrderRequestValidator validator;
    private final PaymentClient payment;
    private final com.stylemind.commerce.service.order.CodOrderConfirmationService codConfirmation;

    public OrderPaymentController(OrderService orders, OrderRequestValidator validator, PaymentClient payment,
            com.stylemind.commerce.service.order.CodOrderConfirmationService codConfirmation) {
        this.orders = orders;
        this.validator = validator;
        this.payment = payment;
        this.codConfirmation = codConfirmation;
    }

    @PostMapping(path = "/api/v1/orders/{orderId}/payment-attempt", produces = "application/json")
    public ResponseEntity<PaymentAttemptResponse> recover(Authentication authentication, @PathVariable String orderId,
            @RequestBody(required = false) JsonNode body) {
        if (CurrentUser.subject(authentication) == null || authentication.getAuthorities().stream()
                .noneMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER") || a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new ApiOperationException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Customer or Admin role is required");
        }
        if (body != null) {
            throw new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request body is not accepted");
        }
        // Both Admin and Customer are self-only. Read transaction ends before calling Payment.
        var order = orders.get(CurrentUser.subject(authentication), false, validator.pathId(orderId));
        boolean payable = (order.paymentMethod().equals("COD") && java.util.Set.of("PENDING_PAYMENT", "PLACED").contains(order.orderStatus()))
                || (order.paymentMethod().equals("SEPAY_VIETQR") && order.orderStatus().equals("PENDING_PAYMENT"));
        if (!payable || order.acceptedTotalVnd() <= 0) {
            throw new ApiOperationException(HttpStatus.CONFLICT, "ORDER_NOT_PAYABLE", "Order is not in an initial payable state");
        }
        var snapshot = OrderPaymentRequest.from(order);
        var attempt = payment.create(snapshot);
        if ("COD".equals(order.paymentMethod())) codConfirmation.confirm(snapshot, attempt);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(attempt);
    }
}
