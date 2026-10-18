package com.stylemind.notification.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
class JwtDecoderConfiguration {

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${stylemind.security.jwt.jwk-set-uri:}") String jwkSetUri,
            @Value("${stylemind.security.jwt.jws-algorithm:}") String jwsAlgorithm) {
        if (StringUtils.hasText(jwkSetUri) && StringUtils.hasText(jwsAlgorithm)) {
            SignatureAlgorithm algorithm = SignatureAlgorithm.valueOf(jwsAlgorithm.trim());
            return NimbusJwtDecoder.withJwkSetUri(jwkSetUri.trim()).jwsAlgorithm(algorithm).build();
        }

        // Keep startup independent of unconfirmed verification settings while protected routes fail closed.
        return token -> {
            throw new JwtException("JWT verification key source is not configured");
        };
    }
}
