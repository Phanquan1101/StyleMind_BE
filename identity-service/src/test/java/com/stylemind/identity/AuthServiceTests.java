package com.stylemind.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.stylemind.identity.client.notification.NotificationEmailClient;
import com.stylemind.identity.client.notification.payload.EmailDispatch;
import com.stylemind.identity.dto.register.RegisterRequest;
import com.stylemind.identity.dto.register.RegisterResponse;
import com.stylemind.identity.errors.ApiOperationException;
import com.stylemind.identity.errors.ErrorCode;
import com.stylemind.identity.mapper.AuthMapper;
import com.stylemind.identity.repository.AccountRepository;
import com.stylemind.identity.repository.AccountRoleRepository;
import com.stylemind.identity.repository.EmailVerificationRepository;
import com.stylemind.identity.repository.RefreshSessionRepository;
import com.stylemind.identity.security.jwt.JwtTokenService;
import com.stylemind.identity.security.verification.VerificationCodeService;
import com.stylemind.identity.service.AuthService;
import com.stylemind.identity.service.impl.AuthServiceImpl;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.UUID;

class AuthServiceTests {

    @Test
    void registrationEmailIsSentOnlyAfterTransactionCallbackHasReturned() {
        AccountRepository accounts = mock(AccountRepository.class);
        AccountRoleRepository roles = mock(AccountRoleRepository.class);
        EmailVerificationRepository challenges = mock(EmailVerificationRepository.class);
        RefreshSessionRepository sessions = mock(RefreshSessionRepository.class);
        VerificationCodeService codeService = mock(VerificationCodeService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JwtTokenService tokenService = mock(JwtTokenService.class);
        NotificationEmailClient sender = mock(NotificationEmailClient.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        AtomicBoolean transactionCallbackReturned = new AtomicBoolean();

        when(transactions.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            Object result = callback.doInTransaction(new SimpleTransactionStatus());
            transactionCallbackReturned.set(true);
            return result;
        });
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}encoded-value");
        when(codeService.generateCode()).thenReturn("012345");
        when(codeService.digest(any(UUID.class), any(UUID.class), anyString())).thenReturn(new byte[32]);
        doAnswer(invocation -> {
            assertThat(transactionCallbackReturned).isTrue();
            return null;
        }).when(sender).send(any(EmailDispatch.class));

        AuthService service = new AuthServiceImpl(accounts, roles, challenges, sessions, codeService,
                passwordEncoder, tokenService, sender, new AuthMapper(),
                Clock.fixed(Instant.parse("2026-10-28T00:00:00Z"), ZoneOffset.UTC), transactions);
        RegisterRequest request = new RegisterRequest("customer@example.com", "raw-password-is-not-logged");

        RegisterResponse response = service.register(request);

        InOrder order = inOrder(transactions, sender);
        order.verify(transactions).execute(any(TransactionCallback.class));
        order.verify(sender).send(any(EmailDispatch.class));
        assertThat(response.email()).isEqualTo("customer@example.com");
        assertThat(response.accountStatus()).isEqualTo("PENDING_VERIFICATION");
        assertThat(request.toString()).doesNotContain("raw-password-is-not-logged");
    }

    @Test
    void duplicateEmailIsRejectedBeforePasswordHashingOrWrites() {
        AccountRepository accounts = mock(AccountRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        VerificationCodeService codeService = mock(VerificationCodeService.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });
        when(accounts.existsByEmail("customer@example.com")).thenReturn(true);
        AuthService service = new AuthServiceImpl(accounts, mock(AccountRoleRepository.class),
                mock(EmailVerificationRepository.class), mock(RefreshSessionRepository.class),
                codeService, passwordEncoder, mock(JwtTokenService.class),
                mock(NotificationEmailClient.class), new AuthMapper(),
                Clock.fixed(Instant.parse("2026-10-28T00:00:00Z"), ZoneOffset.UTC), transactions);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.register(
                new RegisterRequest("Customer@Example.com", "valid-password")))
                .isInstanceOf(ApiOperationException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
        org.mockito.Mockito.verify(passwordEncoder, org.mockito.Mockito.never()).encode("valid-password");
        org.mockito.Mockito.verify(accounts, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void databaseUniqueConstraintRaceIsMappedToDuplicateEmail() {
        AccountRepository accounts = mock(AccountRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        VerificationCodeService codeService = mock(VerificationCodeService.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            callback.doInTransaction(new SimpleTransactionStatus());
            throw new DataIntegrityViolationException("unique constraint race");
        });
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}encoded");
        when(codeService.generateCode()).thenReturn("012345");
        when(codeService.digest(any(UUID.class), any(UUID.class), anyString())).thenReturn(new byte[32]);
        when(accounts.existsByEmail("customer@example.com")).thenReturn(false);
        AuthService service = new AuthServiceImpl(accounts, mock(AccountRoleRepository.class),
                mock(EmailVerificationRepository.class), mock(RefreshSessionRepository.class),
                codeService, passwordEncoder, mock(JwtTokenService.class),
                mock(NotificationEmailClient.class), new AuthMapper(),
                Clock.fixed(Instant.parse("2026-10-28T00:00:00Z"), ZoneOffset.UTC), transactions);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.register(
                new RegisterRequest("customer@example.com", "valid-password")))
                .isInstanceOf(ApiOperationException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
    }
}
