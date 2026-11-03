package com.stylemind.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.stylemind.identity.entity.Account;
import com.stylemind.identity.entity.AccountRole;
import com.stylemind.identity.mapper.UserMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserMapperTests {

    private final UserMapper mapper = new UserMapper();

    @Test
    void mapsManagedUserDtosAndAccountRoles() {
        UUID accountId = UUID.randomUUID();
        OffsetDateTime registeredAt = OffsetDateTime.parse("2026-10-28T10:00:00Z");
        Account account = new Account();
        account.setAccountId(accountId);
        account.setEmail("admin@example.test");
        account.setPasswordHash("must-not-be-exposed");
        account.setAccountStatus("ACTIVE");
        account.setRegisteredAt(registeredAt);

        var response = mapper.response(account, List.of("CUSTOMER", "ADMIN"));
        assertThat(response.accountId()).isEqualTo(accountId);
        assertThat(response.roles()).containsExactly("ADMIN", "CUSTOMER");
        assertThat(mapper.roles(accountId, List.of("CUSTOMER", "ADMIN")).roles())
                .containsExactly("ADMIN", "CUSTOMER");

        List<AccountRole> roles = mapper.accountRoles(accountId, List.of("CUSTOMER", "ADMIN"));
        assertThat(roles).extracting(AccountRole::getAccountId).containsOnly(accountId);
        assertThat(roles).extracting(AccountRole::getRoleCode).containsExactly("CUSTOMER", "ADMIN");

        var page = mapper.page(List.of(response), 0, 20, 1, 1);
        assertThat(page.items()).containsExactly(response);
        assertThat(page.totalElements()).isEqualTo(1);
    }
}
