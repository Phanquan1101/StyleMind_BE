package com.stylemind.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stylemind.payment.vnpay")
public record VnpayPaymentProperties(String tmnCode, String hashSecret, String payUrl, String returnUrl) {
    public VnpayPaymentProperties {
        tmnCode = value(tmnCode);
        hashSecret = value(hashSecret);
        payUrl = value(payUrl, "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        returnUrl = value(returnUrl);
    }

    public boolean configured() {
        return !tmnCode.isBlank() && !hashSecret.isBlank() && !returnUrl.isBlank();
    }

    private static String value(String value) { return value == null ? "" : value.trim(); }
    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
