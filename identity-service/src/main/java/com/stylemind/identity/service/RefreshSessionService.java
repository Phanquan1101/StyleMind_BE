package com.stylemind.identity.service;

import com.stylemind.identity.dto.LoginResponse;
import com.stylemind.identity.errors.InvalidRefreshTokenException;
import com.stylemind.identity.errors.MalformedRefreshTokenException;
import com.stylemind.identity.model.RefreshSession;
import com.stylemind.identity.repository.RefreshSessionRepository;
import com.stylemind.identity.validation.RefreshTokenFormat;
import org.springframework.stereotype.Service;

@Service
public class RefreshSessionService {

    private final RefreshSessionRepository refreshSessionRepository;
    private final JwtTokenService tokenService;
    private final RefreshSessionTransactionService transactionService;

    public RefreshSessionService(
            RefreshSessionRepository refreshSessionRepository,
            JwtTokenService tokenService,
            RefreshSessionTransactionService transactionService) {
        this.refreshSessionRepository = refreshSessionRepository;
        this.tokenService = tokenService;
        this.transactionService = transactionService;
    }

    public LoginResponse refresh(String refreshToken) {
        String tokenHash = digest(refreshToken);
        RefreshSession snapshot = refreshSessionRepository.findByTokenHash(tokenHash)
                .orElseThrow(InvalidRefreshTokenException::new);
        return transactionService.rotate(snapshot.accountId(), tokenHash);
    }

    public void logout(String refreshToken) {
        String tokenHash = digest(refreshToken);
        refreshSessionRepository.findByTokenHash(tokenHash)
                .ifPresent(snapshot -> transactionService.revoke(snapshot.accountId(), tokenHash));
    }

    private String digest(String refreshToken) {
        if (!RefreshTokenFormat.isValid(refreshToken)) {
            throw new MalformedRefreshTokenException();
        }
        return tokenService.refreshTokenHash(refreshToken);
    }
}
