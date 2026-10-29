package com.stylemind.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stylemind.identity.dto.internal.InternalAccountItem;
import com.stylemind.identity.repository.AccountRepository;
import com.stylemind.identity.service.impl.InternalAccountLookupServiceImpl;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

class InternalAccountLookupServiceTests {

    @Test
    void mapsRepositoryRowsIntoSafeInternalResponseDto() {
        AccountRepository repository = mock(AccountRepository.class);
        UUID accountId = UUID.fromString("f0000000-0000-4000-8000-000000000001");
        when(repository.findInternalLookupRows(List.of(accountId))).thenReturn(Collections.singletonList(
                new Object[] {accountId, "customer@example.test", "Customer Name", "ACTIVE"}));

        var response = new InternalAccountLookupServiceImpl(repository).lookup(List.of(accountId));

        assertThat(response.items()).containsExactly(new InternalAccountItem(
                accountId, "customer@example.test", "Customer Name", "ACTIVE"));
        verify(repository).findInternalLookupRows(List.of(accountId));
    }
}
