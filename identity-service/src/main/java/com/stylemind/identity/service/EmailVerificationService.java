package com.stylemind.identity.service;

import com.stylemind.identity.errors.ApiOperationException;
import com.stylemind.identity.errors.EmailDeliveryException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class EmailVerificationService {

    private final EmailVerificationPersistence persistence;
    private final NotificationEmailClient emailClient;

    public EmailVerificationService(EmailVerificationPersistence persistence, NotificationEmailClient emailClient) {
        this.persistence = persistence;
        this.emailClient = emailClient;
    }

    public VerificationOutcome verify(String email, String code) {
        return persistence.verify(email, code);
    }

    public VerificationOutcome resend(String email) {
        ResendCommit commit = persistence.reserveResend(email);
        if (commit.dispatch() == null) {
            return commit.outcome();
        }
        try {
            emailClient.send(commit.dispatch());
        } catch (EmailDeliveryException exception) {
            throw new ApiOperationException(exception.status(), exception.code(),
                    "Email delivery could not be confirmed. The account remains PENDING_VERIFICATION.", 0);
        }
        return VerificationOutcome.of(VerificationOutcome.Code.VERIFIED);
    }

    public void requireSuccess(VerificationOutcome outcome) {
        switch (outcome.code()) {
            case VERIFIED -> { }
            case INVALID -> throw error(HttpStatus.BAD_REQUEST, "EMAIL_VERIFICATION_INVALID",
                    "Email verification could not be completed", outcome);
            case EXPIRED -> throw error(HttpStatus.BAD_REQUEST, "EMAIL_VERIFICATION_EXPIRED",
                    "The verification code has expired", outcome);
            case ATTEMPTS_EXCEEDED -> throw error(HttpStatus.TOO_MANY_REQUESTS,
                    "EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED", "Email verification is temporarily locked", outcome);
            case SEND_LIMIT_EXCEEDED -> throw error(HttpStatus.TOO_MANY_REQUESTS,
                    "EMAIL_VERIFICATION_SEND_LIMIT_EXCEEDED", "A new verification email cannot be sent yet", outcome);
            case ALREADY_VERIFIED -> throw error(HttpStatus.CONFLICT, "EMAIL_ALREADY_VERIFIED",
                    "The email address is already verified", outcome);
            case NOT_ALLOWED -> throw error(HttpStatus.CONFLICT, "EMAIL_VERIFICATION_NOT_ALLOWED",
                    "Email verification is not allowed for this account", outcome);
        }
    }

    private ApiOperationException error(HttpStatus status, String code, String detail, VerificationOutcome outcome) {
        return new ApiOperationException(status, code, detail, outcome.retryAfterSeconds());
    }
}
