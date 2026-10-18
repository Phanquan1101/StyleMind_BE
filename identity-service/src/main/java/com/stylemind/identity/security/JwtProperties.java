package com.stylemind.identity.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stylemind.jwt")
public record JwtProperties(
        String issuer,
        String audience,
        String privateKeyPath,
        Duration accessTtl,
        Duration refreshTtl) {
}
