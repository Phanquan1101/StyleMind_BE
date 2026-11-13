package com.stylemind.commerce.controller.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.commerce.client.payment.OrderPaymentRequest;
import com.stylemind.commerce.client.payment.PaymentAttemptResponse;
import com.stylemind.commerce.client.payment.PaymentClient;
import com.stylemind.commerce.dto.order.CheckoutPriceResponse;
import com.stylemind.commerce.dto.order.OrderResponse;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.security.GuestOrderAccess;
import com.stylemind.commerce.service.order.CodOrderConfirmationService;
import com.stylemind.commerce.service.order.OrderService;
import com.stylemind.commerce.validation.order.GuestCheckoutValidator;
import com.stylemind.commerce.validation.order.OrderRequestValidator;
import com.stylemind.commerce.validation.order.CheckoutSelectionValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class GuestOrderController {
    private final OrderService orders;
    private final GuestCheckoutValidator checkoutValidator;
    private final OrderRequestValidator ids;
    private final PaymentClient payment;
    private final CodOrderConfirmationService codConfirmation;
    private final CheckoutSelectionValidator selections;

    @PostMapping(path = "/api/v1/guest/checkout/recover", consumes = "application/json", produces = "application/json")
    public ResponseEntity<OrderResponse> recoverCheckout(@RequestHeader(name = "X-Guest-Session-Key", required = false) String key,
            @RequestBody JsonNode body) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(orders.recoverGuestCheckout(GuestOrderAccess.requireKey(key), selections.recoveryKey(body)));
    }

    @PostMapping(path = {"/api/v1/guest/checkout/selected/prices", "/api/v1/guest/checkout/buy-now/prices"}, consumes = "application/json", produces = "application/json")
    public ResponseEntity<CheckoutPriceResponse> previewSelection(@RequestHeader(name = "X-Guest-Session-Key", required = false) String key,
            HttpServletRequest request, @RequestBody JsonNode body) {
        boolean buyNow = request.getRequestURI().contains("/buy-now/");
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(orders.previewGuestSelection(GuestOrderAccess.requireKey(key), selections.selection(body, buyNow, false, true)));
    }

    @PostMapping(path = {"/api/v1/guest/orders/selected", "/api/v1/guest/orders/buy-now"}, consumes = "application/json", produces = "application/json")
    public ResponseEntity<OrderResponse> checkoutSelection(@RequestHeader(name = "X-Guest-Session-Key", required = false) String key,
            HttpServletRequest request, @RequestBody JsonNode body) {
        GuestOrderAccess.requireKey(key);
        boolean buyNow = request.getRequestURI().endsWith("/buy-now");
        var selection = selections.selection(body, buyNow, true, true);
        OrderResponse created = orders.checkoutGuestSelection(key, checkoutValidator.checkout(selections.guestCheckoutFields(body)), selection);
        if ("COD".equals(created.paymentMethod())) created = orders.getGuest(key, created.orderId());
        return ResponseEntity.created(URI.create("/api/v1/guest/orders/" + created.orderId())).header("Cache-Control", "no-store").body(created);
    }

    @GetMapping(path = "/api/v1/guest/checkout/prices", produces = "application/json")
    public ResponseEntity<CheckoutPriceResponse> preview(
            @RequestHeader(name = "X-Guest-Session-Key", required = false) String key) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(orders.previewGuest(GuestOrderAccess.requireKey(key)));
    }

    @PostMapping(path = "/api/v1/guest/orders", consumes = "application/json", produces = "application/json")
    public ResponseEntity<OrderResponse> checkout(
            @RequestHeader(name = "X-Guest-Session-Key", required = false) String key, @RequestBody JsonNode body) {
        GuestOrderAccess.requireKey(key);
        OrderResponse created = orders.checkoutGuest(key, checkoutValidator.checkout(body));
        // The transaction and synchronous AFTER_COMMIT payment handoff have completed.
        if ("COD".equals(created.paymentMethod())) created = orders.getGuest(key, created.orderId());
        return ResponseEntity.created(URI.create("/api/v1/guest/orders/" + created.orderId()))
                .header("Cache-Control", "no-store").body(created);
    }

    @GetMapping(path = "/api/v1/guest/orders/{orderId}", produces = "application/json")
    public ResponseEntity<OrderResponse> get(
            @RequestHeader(name = "X-Guest-Session-Key", required = false) String key, @PathVariable String orderId) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(orders.getGuest(GuestOrderAccess.requireKey(key), ids.pathId(orderId)));
    }

    @PostMapping(path = "/api/v1/guest/orders/{orderId}/payment-attempt", produces = "application/json")
    public ResponseEntity<PaymentAttemptResponse> recover(
            @RequestHeader(name = "X-Guest-Session-Key", required = false) String key, @PathVariable String orderId,
            @RequestBody(required = false) JsonNode body) {
        GuestOrderAccess.requireKey(key);
        if (body != null) throw new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request body is not accepted");
        var order = orders.getGuest(key, ids.pathId(orderId));
        boolean payable = ("COD".equals(order.paymentMethod()) && Set.of("PENDING_PAYMENT", "PLACED").contains(order.orderStatus()))
                || (("SEPAY_VIETQR".equals(order.paymentMethod()) || "VNPAY".equals(order.paymentMethod()))
                    && "PENDING_PAYMENT".equals(order.orderStatus()));
        if (!payable || order.acceptedTotalVnd() <= 0) throw new ApiOperationException(HttpStatus.CONFLICT,
                "ORDER_NOT_PAYABLE", "Order is not in an initial payable state");
        var snapshot = OrderPaymentRequest.from(order);
        var attempt = payment.create(snapshot);
        if ("COD".equals(order.paymentMethod())) codConfirmation.confirm(snapshot, attempt);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(attempt);
    }
}
