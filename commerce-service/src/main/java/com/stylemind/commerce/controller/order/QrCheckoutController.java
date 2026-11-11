package com.stylemind.commerce.controller.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.commerce.client.payment.OrderPaymentRequest;
import com.stylemind.commerce.client.payment.PaymentAttemptResponse;
import com.stylemind.commerce.client.payment.PaymentClient;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.dto.order.QrCheckoutListResponse;
import com.stylemind.commerce.security.CurrentUser;
import com.stylemind.commerce.service.order.OrderService;
import com.stylemind.commerce.service.order.QrCheckoutReservationService;
import com.stylemind.commerce.validation.order.CheckoutSelectionValidator;
import com.stylemind.commerce.validation.order.OrderRequestValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class QrCheckoutController {
    private final OrderService orders;
    private final QrCheckoutReservationService checkouts;
    private final PaymentClient payments;
    private final OrderRequestValidator validator;
    private final CheckoutSelectionValidator selections;

    @PostMapping(path = {"/api/v1/qr-checkouts/selected", "/api/v1/qr-checkouts/buy-now"}, consumes = "application/json")
    public ResponseEntity<QrCheckoutReservationService.PaymentContext> prepare(Authentication auth,
            HttpServletRequest request, @RequestBody JsonNode body) {
        UUID owner = subject(auth);
        var selection = selections.selection(body, request.getRequestURI().endsWith("/buy-now"), true);
        UUID id = orders.prepareQrCheckout(owner, request.getHeader(HttpHeaders.AUTHORIZATION),
                validator.checkout(selections.accountCheckoutFields(body)), selection);
        return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store")
                .body(checkouts.paymentContext(id, owner));
    }

    @GetMapping("/api/v1/qr-checkouts/{id}")
    public ResponseEntity<QrCheckoutReservationService.PaymentContext> status(Authentication auth, @PathVariable String id) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(checkouts.paymentContext(validator.pathId(id), subject(auth)));
    }

    @GetMapping("/api/v1/qr-checkouts/pending")
    public ResponseEntity<QrCheckoutListResponse> pending(Authentication auth, HttpServletRequest request) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(orders.listPendingQrCheckouts(subject(auth), validator.pendingQrList(request)));
    }

    @GetMapping("/api/v1/qr-checkouts")
    public ResponseEntity<QrCheckoutListResponse> recentUnpaid(Authentication auth, HttpServletRequest request) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(orders.listRecentUnpaidQrCheckouts(subject(auth), validator.pendingQrList(request)));
    }

    @PostMapping(path = "/api/v1/qr-checkouts/recover", consumes = "application/json")
    public ResponseEntity<QrCheckoutReservationService.PaymentContext> recover(Authentication auth, @RequestBody JsonNode body) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(checkouts.recoverPaymentContext(subject(auth), selections.recoveryKey(body)));
    }

    @PostMapping("/api/v1/qr-checkouts/{id}/payment-attempt")
    public ResponseEntity<PaymentAttemptResponse> attempt(Authentication auth, @PathVariable String id) {
        UUID owner = subject(auth), checkoutId = validator.pathId(id);
        var context = checkouts.paymentContext(checkoutId, owner);
        OffsetDateTime now = OffsetDateTime.now();
        if (!Set.of("PREPARING", "AWAITING_PAYMENT").contains(context.status()) || !now.isBefore(context.expiresAt())) {
            if (!now.isBefore(context.expiresAt())) checkouts.expire(checkoutId, now);
            throw unavailable();
        }
        var attempt = payments.create(new OrderPaymentRequest(checkoutId, owner, "SEPAY_VIETQR",
                context.totalVnd(), "PENDING_PAYMENT", "initial-" + checkoutId));
        if (!checkouts.recordPaymentAttempt(checkoutId, owner, attempt, OffsetDateTime.now())) throw unavailable();
        var expiresAt = attempt.expiresAt().isBefore(context.expiresAt().toInstant())
                ? attempt.expiresAt() : context.expiresAt().toInstant();
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(new PaymentAttemptResponse(attempt.id(), attempt.orderId(), attempt.method(), attempt.amountVnd(),
                        attempt.reference(), attempt.status(), expiresAt, attempt.transferContent(), attempt.qrPayload()));
    }

    @PostMapping("/api/v1/qr-checkouts/{id}/cancel")
    public ResponseEntity<QrCheckoutReservationService.PaymentContext> cancel(Authentication auth, @PathVariable String id) {
        UUID owner = subject(auth), checkoutId = validator.pathId(id);
        checkouts.cancel(checkoutId, owner, null, OffsetDateTime.now());
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(checkouts.paymentContext(checkoutId, owner));
    }

    private UUID subject(Authentication auth) {
        UUID id = CurrentUser.subject(auth);
        if (id == null) throw new ApiOperationException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
        return id;
    }
    private ApiOperationException unavailable() {
        return new ApiOperationException(HttpStatus.CONFLICT, "CHECKOUT_NOT_PAYABLE", "Checkout is no longer payable");
    }
}
