package com.stylemind.stylist.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.util.StringUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
class JwtDecoderConfiguration {

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${stylemind.security.jwt.jwk-set-uri:}") String jwkSetUri,
            @Value("${stylemind.security.jwt.jws-algorithm:RS256}") String jwsAlgorithm,
            @Value("${stylemind.security.jwt.issuer:https://identity.stylemind.local}") String issuer,
            @Value("${stylemind.security.jwt.audience:stylemind-api}") String audience) {
        if (StringUtils.hasText(jwkSetUri)) {
            if (!"RS256".equals(jwsAlgorithm.trim())) {
                throw new IllegalStateException("Stylist accepts only RS256 access tokens");
            }
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri.trim())
                    .jwsAlgorithm(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256)
                    .build();
            decoder.setJwtValidator(JwtContractValidator.forIdentity(issuer, audience));
            return decoder;
        }

        // Keep startup independent of unconfirmed verification settings while protected routes fail closed.
        return token -> {
            throw new JwtException("JWT verification key source is not configured");
        };
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        Converter<Jwt, Collection<GrantedAuthority>> authorities = jwt -> {
            Collection<GrantedAuthority> result = new ArrayList<>(scopes.convert(jwt));
            Object rolesClaim = jwt.getClaims().get("roles");
            if (rolesClaim instanceof Collection<?> roles) {
                for (Object role : roles) {
                    if ("CUSTOMER".equals(role) || "ADMIN".equals(role)) {
                        result.add(new SimpleGrantedAuthority("ROLE_" + role));
                    }
                }
            }
            return result;
        };
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
