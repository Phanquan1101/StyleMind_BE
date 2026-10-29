package com.stylemind.payment.service.impl;

import com.stylemind.payment.errors.PaymentApiException;
import com.stylemind.payment.service.PaymentAuthorizationService;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class PaymentAuthorizationServiceImpl implements PaymentAuthorizationService {

    @Override
    public UUID customerSubject(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (RuntimeException exception) {
            throw new PaymentApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "JWT subject is not a customer UUID");
        }
    }

    @Override
    public UUID adminSubject(Jwt jwt) {
        if (!hasRole(jwt, "ADMIN")) {
            throw new PaymentApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Admin role is required");
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (RuntimeException exception) {
            throw new PaymentApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "JWT subject is not an admin UUID");
        }
    }

    private boolean hasRole(Jwt jwt, String expectedRole) {
        Collection<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null) {
            roles = jwt.getClaimAsStringList("role");
        }
        if (roles == null) {
            return false;
        }
        String expected = expectedRole.toUpperCase(Locale.ROOT);
        return roles.stream()
                .filter(role -> role != null)
                .map(role -> role.replaceFirst("^ROLE_", "").toUpperCase(Locale.ROOT))
                .anyMatch(expected::equals);
    }
}
