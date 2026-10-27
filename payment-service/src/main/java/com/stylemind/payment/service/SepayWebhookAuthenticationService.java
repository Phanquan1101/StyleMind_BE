package com.stylemind.payment.service;

import com.stylemind.payment.config.SepayPaymentProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
public class SepayWebhookAuthenticationService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final String SEPAY_SIGNATURE_HEADER = "X-SePay-Signature";
    private static final String SEPAY_TIMESTAMP_HEADER = "X-SePay-Timestamp";

    private final SepayPaymentProperties properties;

    public SepayWebhookAuthenticationService(SepayPaymentProperties properties) {
        this.properties = properties;
    }

    public boolean isAuthentic(byte[] rawBody, HttpHeaders headers, Instant now) {
        String mode = properties.webhookAuthMode().toLowerCase();
        return switch (mode) {
            case "none" -> true;
            case "api-key", "apikey" -> hasValidApiKey(headers);
            case "hmac", "hmac-sha256" -> hasValidHmac(rawBody, headers, now);
            default -> false;
        };
    }

    private boolean hasValidApiKey(HttpHeaders headers) {
        String expected = properties.webhookApiKey();
        String actual = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (expected.isBlank() || actual == null || !actual.startsWith("Apikey ")) {
            return false;
        }
        return constantTimeEquals(expected, actual.substring("Apikey ".length()));
    }

    private boolean hasValidHmac(byte[] rawBody, HttpHeaders headers, Instant now) {
        String secret = properties.webhookSecret();
        String signature = headers.getFirst(SEPAY_SIGNATURE_HEADER);
        String timestampHeader = headers.getFirst(SEPAY_TIMESTAMP_HEADER);
        if (secret.isBlank() || signature == null || timestampHeader == null) {
            return false;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (NumberFormatException exception) {
            return false;
        }

        long skew = Math.abs(now.getEpochSecond() - timestamp);
        if (skew > properties.allowedTimestampSkew().toSeconds()) {
            return false;
        }

        String signedPayload = timestamp + "." + new String(rawBody, StandardCharsets.UTF_8);
        String expected = SIGNATURE_PREFIX + hmacSha256Hex(secret, signedPayload);
        return constantTimeEquals(expected, signature);
    }

    private String hmacSha256Hex(String secret, String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to verify SePay webhook signature", exception);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
