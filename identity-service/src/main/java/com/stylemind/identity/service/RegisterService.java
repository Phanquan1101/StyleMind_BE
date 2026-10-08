package com.stylemind.identity.service;

import com.stylemind.identity.dto.RegisterRequest;
import com.stylemind.identity.dto.RegisterResponse;
import com.stylemind.identity.errors.ApiOperationException;
import com.stylemind.identity.errors.EmailDeliveryException;
import org.springframework.stereotype.Service;

@Service
public class RegisterService {

    private final EmailVerificationPersistence persistence;
    private final NotificationEmailClient emailClient;

    public RegisterService(EmailVerificationPersistence persistence, NotificationEmailClient emailClient) {
        this.persistence = persistence;
        this.emailClient = emailClient;
    }

    public RegisterResponse register(RegisterRequest request) {
        RegistrationCommit committed = persistence.createRegistration(request);
        try {
            emailClient.send(committed.dispatch());
        } catch (EmailDeliveryException exception) {
            throw ApiOperationException.registrationDeliveryFailure(exception);
        }
        return committed.response();
    }
}
