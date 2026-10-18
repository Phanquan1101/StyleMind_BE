package com.stylemind.identity.service.register.impl;

import com.stylemind.identity.service.register.RegisterService;
import com.stylemind.identity.client.notification.NotificationEmailClient;
import com.stylemind.identity.model.register.RegistrationCommit;
import com.stylemind.identity.service.emailverification.internal.EmailVerificationPersistence;
import com.stylemind.identity.dto.register.RegisterRequest;
import com.stylemind.identity.dto.register.RegisterResponse;
import com.stylemind.identity.errors.ApiOperationException;
import com.stylemind.identity.errors.EmailDeliveryException;
import org.springframework.stereotype.Service;

@Service
public class RegisterServiceImpl implements RegisterService {

    private final EmailVerificationPersistence persistence;
    private final NotificationEmailClient emailClient;

    public RegisterServiceImpl(EmailVerificationPersistence persistence, NotificationEmailClient emailClient) {
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
