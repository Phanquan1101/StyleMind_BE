package com.stylemind.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stylemind.payment.commerce")
public record CommerceClientProperties(
        String baseUrl,
        String serviceToken) {

    public CommerceClientProperties {
        baseUrl = baseUrl == null ? "" : baseUrl.trim();
        serviceToken = serviceToken == null ? "" : serviceToken.trim();
    }
}
