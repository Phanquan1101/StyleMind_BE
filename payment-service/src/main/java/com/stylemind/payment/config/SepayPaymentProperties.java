package com.stylemind.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stylemind.payment.sepay")
public record SepayPaymentProperties(
        String qrBankName,
        String qrAccountNumber,
        String qrAccountName,
        String paymentCodePrefix) {

    public SepayPaymentProperties {
        qrBankName = defaultIfBlank(qrBankName, "");
        qrAccountNumber = defaultIfBlank(qrAccountNumber, "");
        qrAccountName = defaultIfBlank(qrAccountName, "");
        paymentCodePrefix = defaultIfBlank(paymentCodePrefix, "SM");
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
