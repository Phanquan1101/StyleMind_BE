package com.stylemind.payment.controller;

import com.stylemind.payment.dto.ConfirmCodCollectionRequest;
import com.stylemind.payment.dto.CreatePaymentAttemptRequest;
import com.stylemind.payment.dto.PaymentAttemptResponse;
import com.stylemind.payment.dto.PaymentExceptionListResponse;
import com.stylemind.payment.service.PaymentAttemptService;
import com.stylemind.payment.service.PaymentAuthorizationService;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentAttemptController {

    private final PaymentAttemptService paymentAttemptService;
    private final PaymentAuthorizationService authorizationService;

    public PaymentAttemptController(
            PaymentAttemptService paymentAttemptService,
            PaymentAuthorizationService authorizationService) {
        this.paymentAttemptService = paymentAttemptService;
        this.authorizationService = authorizationService;
    }

    @PostMapping(value = "/api/v1/payments/attempts", consumes = "application/json", produces = "application/json")
    ResponseEntity<PaymentAttemptResponse> createPaymentAttempt(
            @RequestBody CreatePaymentAttemptRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        PaymentAttemptResponse response = paymentAttemptService.createAttempt(
                request,
                authorizationService.customerSubject(jwt),
                Instant.now());
        return ResponseEntity.created(URI.create("/api/v1/payments/attempts/" + response.id()))
                .body(response);
    }

    @PostMapping(value = "/api/v1/admin/cod-collections", consumes = "application/json", produces = "application/json")
    ResponseEntity<PaymentAttemptResponse> confirmCodCollection(
            @RequestBody com.fasterxml.jackson.databind.JsonNode body,
            @AuthenticationPrincipal Jwt jwt) {
        java.util.UUID adminId = authorizationService.adminSubject(jwt);
        PaymentAttemptResponse response = paymentAttemptService.confirmCodCollection(
                ConfirmCodCollectionRequest.parse(body),
                adminId,
                Instant.now());
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(response);
    }

    @GetMapping(value = "/api/v1/admin/payment-exceptions", produces = "application/json")
    ResponseEntity<PaymentExceptionListResponse> listPaymentExceptions(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            @RequestParam(name = "offset", defaultValue = "0") int offset,
            @AuthenticationPrincipal Jwt jwt) {
        authorizationService.adminSubject(jwt);
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(paymentAttemptService.listExceptions(status, limit, offset));
    }
}
