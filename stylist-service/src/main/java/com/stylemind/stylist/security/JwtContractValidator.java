package com.stylemind.stylist.security;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;

public final class JwtContractValidator {

    private JwtContractValidator() {
    }

    public static OAuth2TokenValidator<Jwt> forIdentity(String issuer, String audience) {
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                "aud", values -> values != null && values.contains(audience));
        OAuth2TokenValidator<Jwt> identityClaims = jwt -> {
            if (jwt.getIssuedAt() == null || jwt.getExpiresAt() == null
                    || !validUuid(jwt.getSubject()) || !validUuid(jwt.getId())
                    || jwt.getIssuedAt().isAfter(Instant.now().plusSeconds(60))) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        OAuth2ErrorCodes.INVALID_TOKEN, "Required JWT claims are missing or invalid", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), audienceValidator, identityClaims);
    }

    private static boolean validUuid(String value) {
        if (value == null) return false;
        try {
            UUID parsed = UUID.fromString(value);
            return parsed.toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
