package com.stylemind.identity.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "stylemind.jwt")
public record JwtProperties(
        String issuer,
        String audience,
        String privateKeyPath,
        Duration accessTtl,
        Duration refreshTtl) {
}
