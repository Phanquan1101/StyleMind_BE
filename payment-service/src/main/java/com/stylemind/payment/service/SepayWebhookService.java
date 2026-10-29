package com.stylemind.payment.service;

import com.stylemind.payment.model.SepayWebhookDecision;
import java.time.Instant;
import org.springframework.http.HttpHeaders;

public interface SepayWebhookService {
    SepayWebhookDecision receive(byte[] rawBody, HttpHeaders headers, Instant receivedAt);

    class SepayWebhookUnauthorizedException extends RuntimeException {
    }

    class SepayWebhookMalformedException extends RuntimeException {
        public SepayWebhookMalformedException(Throwable cause) {
            super(cause);
        }
    }
}
