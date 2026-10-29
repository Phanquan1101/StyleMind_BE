package com.stylemind.payment.service;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

public interface PaymentAuthorizationService {
    UUID customerSubject(Jwt jwt);
    UUID adminSubject(Jwt jwt);
}
