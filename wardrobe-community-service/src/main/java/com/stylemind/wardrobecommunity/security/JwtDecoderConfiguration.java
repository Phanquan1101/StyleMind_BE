package com.stylemind.wardrobecommunity.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
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

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopesConverter = new JwtGrantedAuthoritiesConverter();
        Converter<Jwt, Collection<GrantedAuthority>> authoritiesConverter = jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>(scopesConverter.convert(jwt));
            Object claim = jwt.getClaims().get("roles");
            if (claim instanceof Collection<?> roleCodes) {
                for (Object roleCode : roleCodes) {
                    if ("CUSTOMER".equals(roleCode) || "ADMIN".equals(roleCode)) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + roleCode));
                    }
                }
            }
            return authorities;
        };
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}
