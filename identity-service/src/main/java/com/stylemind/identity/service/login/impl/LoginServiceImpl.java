package com.stylemind.identity.service.login.impl;

import com.stylemind.identity.service.login.LoginService;
import com.stylemind.identity.service.login.internal.LoginSessionService;
import com.stylemind.identity.dto.login.LoginRequest;
import com.stylemind.identity.dto.login.LoginResponse;
import com.stylemind.identity.errors.InvalidCredentialsException;
import com.stylemind.identity.model.login.LoginAccount;
import com.stylemind.identity.repository.account.AccountRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class LoginServiceImpl implements LoginService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginSessionService loginSessionService;
    private final String timingEqualizationHash;

    public LoginServiceImpl(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            LoginSessionService loginSessionService) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginSessionService = loginSessionService;
        this.timingEqualizationHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        Optional<LoginAccount> accountResult = accountRepository.findForLogin(normalizedEmail);
        if (accountResult.isEmpty()) {
            passwordEncoder.matches(request.password(), timingEqualizationHash);
            throw new InvalidCredentialsException();
        }

        LoginAccount account = accountResult.get();
        boolean passwordMatches;
        try {
            passwordMatches = passwordEncoder.matches(request.password(), account.passwordHash());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Stored credential cannot be verified", exception);
        }
        if (!passwordMatches) {
            throw new InvalidCredentialsException();
        }

        return loginSessionService.createSession(account.accountId(), account.passwordHash());
    }
}
