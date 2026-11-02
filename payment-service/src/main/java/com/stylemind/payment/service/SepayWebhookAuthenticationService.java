package com.stylemind.payment.service;

import java.time.Instant;
import org.springframework.http.HttpHeaders;

public interface SepayWebhookAuthenticationService {
    boolean isAuthentic(byte[] rawBody, HttpHeaders headers, Instant now);
}
