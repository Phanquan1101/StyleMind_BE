package com.stylemind.identity.service.session.impl;

import com.stylemind.identity.service.session.RefreshSessionService;
import com.stylemind.identity.security.JwtTokenService;
import com.stylemind.identity.service.session.internal.RefreshSessionTransactionService;
import com.stylemind.identity.dto.login.LoginResponse;
import com.stylemind.identity.errors.InvalidRefreshTokenException;
import com.stylemind.identity.errors.MalformedRefreshTokenException;
import com.stylemind.identity.model.session.RefreshSession;
import com.stylemind.identity.entity.RefreshSessionRow;
import com.stylemind.identity.repository.session.RefreshSessionRepository;
import com.stylemind.identity.validation.auth.RefreshTokenFormat;
import org.springframework.stereotype.Service;

@Service
public class RefreshSessionServiceImpl implements RefreshSessionService {

    private final RefreshSessionRepository refreshSessionRepository;
    private final JwtTokenService tokenService;
    private final RefreshSessionTransactionService transactionService;

    public RefreshSessionServiceImpl(
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
                .map(RefreshSessionServiceImpl::snapshot)
                .orElseThrow(InvalidRefreshTokenException::new);
        return transactionService.rotate(snapshot.accountId(), tokenHash);
    }

    public void logout(String refreshToken) {
        String tokenHash = digest(refreshToken);
        refreshSessionRepository.findByTokenHash(tokenHash)
                .map(RefreshSessionServiceImpl::snapshot)
                .ifPresent(snapshot -> transactionService.revoke(snapshot.accountId(), tokenHash));
    }

    private String digest(String refreshToken) {
        if (!RefreshTokenFormat.isValid(refreshToken)) {
            throw new MalformedRefreshTokenException();
        }
        return tokenService.refreshTokenHash(refreshToken);
    }

    private static RefreshSession snapshot(RefreshSessionRow row) {
        return new RefreshSession(row.getSessionId(), row.getAccountId(), row.getTokenHash(),
                row.getIssuedAt(), row.getExpiresAt(), row.getRevokedAt());
    }
}
