package com.stylemind.payment.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.payment.dto.OrderPaymentRequest;
import com.stylemind.payment.dto.PaymentAttemptResponse;
import com.stylemind.payment.service.PaymentAttemptService;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderPaymentController {
    private final PaymentAttemptService payments;

    public OrderPaymentController(PaymentAttemptService payments) {
        this.payments = payments;
    }

    @PostMapping(path = "/internal/v1/payments/order-attempts", consumes = "application/json", produces = "application/json")
    public ResponseEntity<PaymentAttemptResponse> create(@RequestBody JsonNode body) {
        var request = OrderPaymentRequest.parse(body);
        var response = payments.createOrderAttempt(request, Instant.now());
        return ResponseEntity.created(URI.create("/api/v1/payments/attempts/" + response.id()))
                .header("Cache-Control", "no-store").body(response);
    }
}
