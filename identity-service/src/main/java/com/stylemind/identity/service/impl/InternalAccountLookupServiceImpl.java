package com.stylemind.identity.service.impl;

import com.stylemind.identity.dto.internal.InternalAccountItem;
import com.stylemind.identity.dto.internal.InternalAccountLookupResponse;
import com.stylemind.identity.repository.AccountRepository;
import com.stylemind.identity.service.InternalAccountLookupService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InternalAccountLookupServiceImpl implements InternalAccountLookupService {

    private final AccountRepository accounts;

    @Transactional(readOnly = true)
    public InternalAccountLookupResponse lookup(Collection<UUID> accountIds) {
        return new InternalAccountLookupResponse(accounts.findInternalLookupRows(accountIds).stream()
                .map(row -> new InternalAccountItem((UUID) row[0], (String) row[1], (String) row[2], (String) row[3]))
                .toList());
    }
}
