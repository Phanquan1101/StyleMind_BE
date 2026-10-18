package com.stylemind.identity.service.passwordreset.impl;

import com.stylemind.identity.client.notification.NotificationEmailClient;
import com.stylemind.identity.client.notification.payload.PasswordResetEmailDispatch;
import com.stylemind.identity.dto.passwordreset.ForgotPasswordResponse;
import com.stylemind.identity.dto.passwordreset.ResetTokenResponse;
import com.stylemind.identity.errors.ApiOperationException;
import com.stylemind.identity.errors.EmailDeliveryException;
import com.stylemind.identity.model.passwordreset.PasswordResetVerification;
import com.stylemind.identity.repository.passwordreset.PasswordResetRepository;
import com.stylemind.identity.security.PasswordResetCryptography;
import com.stylemind.identity.service.passwordreset.PasswordResetService;
import com.stylemind.identity.service.passwordreset.internal.PasswordResetPersistence;
import com.stylemind.identity.service.passwordreset.internal.PasswordResetTransactionService;
import com.stylemind.identity.validation.auth.PasswordResetTokenFormat;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger("com.stylemind.identity.service.PasswordResetService");
    private static final String GENERIC_REQUEST_MESSAGE =
            "If the account is eligible, password reset instructions will be sent.";

    private final PasswordResetPersistence persistence;
    private final PasswordResetRepository challengeRepository;
    private final PasswordResetCryptography cryptography;
    private final PasswordResetTransactionService transactionService;
    private final NotificationEmailClient emailClient;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetServiceImpl(PasswordResetPersistence persistence, PasswordResetRepository challengeRepository,
            PasswordResetCryptography cryptography, PasswordResetTransactionService transactionService,
            NotificationEmailClient emailClient, PasswordEncoder passwordEncoder) {
        this.persistence = persistence;
        this.challengeRepository = challengeRepository;
        this.cryptography = cryptography;
        this.transactionService = transactionService;
        this.emailClient = emailClient;
        this.passwordEncoder = passwordEncoder;
    }

    public ForgotPasswordResponse requestOtp(String email) {
        PasswordResetEmailDispatch dispatch = persistence.reserveOtp(email);
        if (dispatch != null) {
            try {
                emailClient.sendPasswordReset(dispatch);
            } catch (EmailDeliveryException exception) {
                log.warn("Password reset email delivery did not complete correlationId={} outcome={}",
                        MDC.get("correlationId"), exception.code());
            }
        }
        return new ForgotPasswordResponse(GENERIC_REQUEST_MESSAGE);
    }

    public ResetTokenResponse verifyOtp(String email, String code) {
        PasswordResetVerification result = persistence.verifyOtp(email, code);
        return switch (result.code()) {
            case VERIFIED -> new ResetTokenResponse(result.resetToken(), 300);
            case INVALID -> throw new ApiOperationException(HttpStatus.BAD_REQUEST,
                    "PASSWORD_RESET_VERIFICATION_INVALID", "Password reset verification could not be completed", 0);
            case ATTEMPTS_EXCEEDED -> throw new ApiOperationException(HttpStatus.TOO_MANY_REQUESTS,
                    "PASSWORD_RESET_ATTEMPTS_EXCEEDED", "Password reset verification is temporarily locked",
                    result.retryAfterSeconds());
        };
    }

    public void resetPassword(String resetToken, String newPassword) {
        if (!PasswordResetTokenFormat.isValid(resetToken)) {
            throw new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                    "Request is missing or malformed", 0);
        }
        byte[] tokenHash = cryptography.resetTokenHash(resetToken);
        Optional<UUID> accountId = challengeRepository.findAccountIdByResetTokenHash(tokenHash);
        if (accountId.isEmpty()) {
            throw invalidResetToken();
        }

        String newPasswordHash = passwordEncoder.encode(newPassword);
        if (!transactionService.reset(accountId.get(), tokenHash, newPasswordHash)) {
            throw invalidResetToken();
        }
    }

    private ApiOperationException invalidResetToken() {
        return new ApiOperationException(HttpStatus.BAD_REQUEST, "PASSWORD_RESET_INVALID",
                "Password reset could not be completed", 0);
    }
}
