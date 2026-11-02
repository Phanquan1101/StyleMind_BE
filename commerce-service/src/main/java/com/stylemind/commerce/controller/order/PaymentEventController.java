package com.stylemind.commerce.controller.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.commerce.dto.order.PaymentConfirmedEvent;
import com.stylemind.commerce.dto.order.PaymentEventReceipt;
import com.stylemind.commerce.service.order.PaymentEventService;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class PaymentEventController {
    private final PaymentEventService service;
    public PaymentEventController(PaymentEventService service) { this.service = service; }
    @PostMapping(path = "/internal/v1/payment-events", consumes = "application/json", produces = "application/json")
    public ResponseEntity<PaymentEventReceipt> receive(@RequestBody JsonNode body) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(service.receive(PaymentConfirmedEvent.parse(body), Instant.now()));
    }
}
