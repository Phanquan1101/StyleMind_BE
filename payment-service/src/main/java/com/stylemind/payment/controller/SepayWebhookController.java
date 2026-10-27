package com.stylemind.payment.controller;

import com.stylemind.payment.dto.SepayWebhookResponse;
import com.stylemind.payment.service.SepayWebhookService;
import io.swagger.v3.oas.annotations.Hidden;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
public class SepayWebhookController {

    private final SepayWebhookService webhookService;

    public SepayWebhookController(SepayWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping(value = "/internal/v1/sepay/webhooks", consumes = "application/json", produces = "application/json")
    ResponseEntity<SepayWebhookResponse> receive(
            @RequestBody byte[] rawBody,
            @RequestHeader HttpHeaders headers) {
        webhookService.receive(rawBody, headers, Instant.now());
        return ResponseEntity.ok(new SepayWebhookResponse(true));
    }
}
