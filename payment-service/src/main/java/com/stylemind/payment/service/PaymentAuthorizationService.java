package com.stylemind.payment.service;

import com.stylemind.payment.errors.PaymentApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class PaymentAuthorizationService {

    public UUID customerSubject(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (RuntimeException exception) {
            throw new PaymentApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "JWT subject is not a customer UUID");
        }
    }

}
