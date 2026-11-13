package com.stylemind.commerce.controller.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.commerce.client.payment.OrderPaymentRequest;
import com.stylemind.commerce.client.payment.PaymentAttemptResponse;
import com.stylemind.commerce.client.payment.PaymentClient;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.security.GuestOrderAccess;
import com.stylemind.commerce.service.order.OrderService;
import com.stylemind.commerce.service.order.QrCheckoutReservationService;
import com.stylemind.commerce.validation.order.CheckoutSelectionValidator;
import com.stylemind.commerce.validation.order.GuestCheckoutValidator;
import com.stylemind.commerce.validation.order.OrderRequestValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class GuestQrCheckoutController {
    private final OrderService orders;
    private final QrCheckoutReservationService checkouts;
    private final PaymentClient payments;
    private final OrderRequestValidator ids;
    private final CheckoutSelectionValidator selections;
    private final GuestCheckoutValidator validator;

    @PostMapping(path = {"/api/v1/guest/qr-checkouts/selected", "/api/v1/guest/qr-checkouts/buy-now"}, consumes = "application/json")
    public ResponseEntity<QrCheckoutReservationService.PaymentContext> prepare(
            @RequestHeader(name = "X-Guest-Session-Key", required = false) String guest, HttpServletRequest request, @RequestBody JsonNode body) {
        String key = GuestOrderAccess.requireKey(guest), hash = GuestOrderAccess.digest(key);
        var selection = selections.selection(body, request.getRequestURI().endsWith("/buy-now"), true, true);
        UUID id = orders.prepareGuestQrCheckout(key, validator.checkout(selections.guestCheckoutFields(body)), selection);
        return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store").body(checkouts.paymentContext(id, null, hash));
    }

    @PostMapping(path = "/api/v1/guest/qr-checkouts/recover", consumes = "application/json")
    public ResponseEntity<QrCheckoutReservationService.PaymentContext> recover(@RequestHeader(name = "X-Guest-Session-Key", required = false) String guest, @RequestBody JsonNode body) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(checkouts.recoverGuestPaymentContext(hash(guest), selections.recoveryKey(body)));
    }

    @GetMapping("/api/v1/guest/qr-checkouts/{id}")
    public ResponseEntity<QrCheckoutReservationService.PaymentContext> status(@RequestHeader(name = "X-Guest-Session-Key", required = false) String guest, @PathVariable String id) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(checkouts.paymentContext(ids.pathId(id), null, hash(guest)));
    }

    @PostMapping("/api/v1/guest/qr-checkouts/{id}/payment-attempt")
    public ResponseEntity<PaymentAttemptResponse> attempt(@RequestHeader(name = "X-Guest-Session-Key", required = false) String guest, @PathVariable String id) {
        String hash = hash(guest); UUID checkoutId = ids.pathId(id);
        var context = checkouts.paymentContext(checkoutId, null, hash);
        OffsetDateTime now = OffsetDateTime.now();
        if (!Set.of("PREPARING", "AWAITING_PAYMENT").contains(context.status()) || !now.isBefore(context.expiresAt())) {
            if (!now.isBefore(context.expiresAt())) checkouts.expire(checkoutId, now);
            throw unavailable();
        }
        var attempt = payments.create(new OrderPaymentRequest(checkoutId, null, context.paymentMethod(), context.totalVnd(), "PENDING_PAYMENT", "initial-" + checkoutId));
        if (!checkouts.recordPaymentAttempt(checkoutId, null, hash, attempt, OffsetDateTime.now())) throw unavailable();
        var expires = attempt.expiresAt().isBefore(context.expiresAt().toInstant()) ? attempt.expiresAt() : context.expiresAt().toInstant();
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(new PaymentAttemptResponse(attempt.id(), attempt.orderId(), attempt.method(), attempt.amountVnd(), attempt.reference(), attempt.status(), expires, attempt.transferContent(), attempt.qrPayload(), attempt.paymentUrl()));
    }

    @PostMapping("/api/v1/guest/qr-checkouts/{id}/cancel")
    public ResponseEntity<QrCheckoutReservationService.PaymentContext> cancel(@RequestHeader(name = "X-Guest-Session-Key", required = false) String guest, @PathVariable String id) {
        String hash = hash(guest); UUID checkoutId = ids.pathId(id);
        var context = checkouts.paymentContext(checkoutId, null, hash);
        if ("VNPAY".equals(context.paymentMethod()) && Set.of("PREPARING", "AWAITING_PAYMENT").contains(context.status())) {
            payments.cancelUnpaidVnpay(checkoutId);
        }
        checkouts.cancel(checkoutId, null, hash, OffsetDateTime.now());
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(checkouts.paymentContext(checkoutId, null, hash));
    }
    private String hash(String key) { return GuestOrderAccess.digest(GuestOrderAccess.requireKey(key)); }
    private ApiOperationException unavailable() { return new ApiOperationException(HttpStatus.CONFLICT, "CHECKOUT_NOT_PAYABLE", "Checkout is no longer payable"); }
}
