package com.stylemind.identity.service.internal.impl;

import com.stylemind.identity.dto.internal.AccountEmailVerificationItem;
import com.stylemind.identity.dto.internal.AccountEmailVerificationLookupResponse;
import com.stylemind.identity.repository.account.AccountRepository;
import com.stylemind.identity.service.internal.AccountEmailVerificationLookupService;
import java.util.Collection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountEmailVerificationLookupServiceImpl implements AccountEmailVerificationLookupService {
    private final AccountRepository accounts;

    @Override
    @Transactional(readOnly = true)
    public AccountEmailVerificationLookupResponse lookupByEmail(String email) {
        return new AccountEmailVerificationLookupResponse(accounts.findEmailVerificationFactsByEmail(email).stream()
                .map(row -> new AccountEmailVerificationItem(row.accountId(), row.email(),
                        row.accountStatus(), row.emailVerifiedAt())).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public AccountEmailVerificationLookupResponse lookup(Collection<UUID> accountIds) {
        return new AccountEmailVerificationLookupResponse(accounts.findEmailVerificationFacts(accountIds).stream()
                .map(row -> new AccountEmailVerificationItem(row.accountId(), row.email(),
                        row.accountStatus(), row.emailVerifiedAt())).toList());
    }
}
