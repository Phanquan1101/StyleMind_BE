package com.stylemind.payment.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stylemind.payment.sepay")
public record SepayPaymentProperties(
        String webhookAuthMode,
        String webhookSecret,
        String webhookApiKey,
        Duration allowedTimestampSkew,
        String providerName,
        String qrBankName,
        String qrAccountNumber,
        String qrAccountName,
        String paymentCodePrefix) {

    public SepayPaymentProperties {
        webhookAuthMode = defaultIfBlank(webhookAuthMode, "hmac");
        webhookSecret = defaultIfBlank(webhookSecret, "");
        webhookApiKey = defaultIfBlank(webhookApiKey, "");
        allowedTimestampSkew = allowedTimestampSkew == null ? Duration.ofMinutes(5) : allowedTimestampSkew;
        providerName = defaultIfBlank(providerName, "SEPAY");
        qrBankName = defaultIfBlank(qrBankName, "");
        qrAccountNumber = defaultIfBlank(qrAccountNumber, "");
        qrAccountName = defaultIfBlank(qrAccountName, "");
        paymentCodePrefix = defaultIfBlank(paymentCodePrefix, "SM");
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
