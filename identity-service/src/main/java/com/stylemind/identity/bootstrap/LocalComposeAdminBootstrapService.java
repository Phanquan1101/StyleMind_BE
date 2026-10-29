package com.stylemind.identity.bootstrap;

import com.stylemind.identity.dto.register.RegisterRequest;
import com.stylemind.identity.entity.Account;
import com.stylemind.identity.entity.AccountRole;
import com.stylemind.identity.repository.account.AccountRepository;
import com.stylemind.identity.repository.account.AccountRoleRepository;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local Compose-only first-admin provisioning. The trusted bootstrap credentials are
 * the explicit exception to normal email verification; this class is not enabled
 * outside the compose-local profile and never changes an account that already exists.
 */
@Service
@Profile("compose-local")
public class LocalComposeAdminBootstrapService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalComposeAdminBootstrapService.class);
    private static final String ACTIVE = "ACTIVE";
    private static final String ADMIN = "ADMIN";

    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;
    private final Clock clock;

    public LocalComposeAdminBootstrapService(
            AccountRepository accountRepository,
            AccountRoleRepository accountRoleRepository,
            PasswordEncoder passwordEncoder,
            Validator validator,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.accountRoleRepository = accountRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
        this.clock = clock;
    }

    @Transactional
    public Outcome ensureAdmin(String configuredEmail, String configuredPassword) {
        RegisterRequest credentials = new RegisterRequest(configuredEmail, configuredPassword);
        if (!validator.validate(credentials).isEmpty()) {
            throw new IllegalStateException(
                    "Local Compose bootstrap admin configuration is invalid; check its email and password policy.");
        }

        String email = credentials.email().trim().toLowerCase(Locale.ROOT);
        var existingAccount = accountRepository.findByEmail(email);
        if (existingAccount.isPresent()) {
            Account account = existingAccount.get();
            boolean alreadyEligibleAdmin = ACTIVE.equals(account.getAccountStatus())
                    && account.getEmailVerifiedAt() != null
                    && accountRoleRepository.findByIdAccountIdOrderByIdRoleCodeAsc(account.getAccountId()).stream()
                            .anyMatch(role -> ADMIN.equals(role.getRoleCode()));
            if (alreadyEligibleAdmin) {
                LOGGER.info("Local Compose bootstrap Admin already exists; no account data was changed.");
                return Outcome.ALREADY_PROVISIONED;
            }

            LOGGER.warn("Local Compose bootstrap skipped because its configured email already belongs to an account; "
                    + "existing status, verification and roles were left unchanged.");
            return Outcome.EXISTING_ACCOUNT_UNCHANGED;
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        UUID accountId = UUID.randomUUID();
        Account account = new Account();
        account.setAccountId(accountId);
        account.setEmail(email);
        account.setPasswordHash(passwordEncoder.encode(credentials.password()));
        account.setAccountStatus(ACTIVE);
        account.setEmailVerifiedAt(now);
        account.setRegisteredAt(now);
        accountRepository.saveAndFlush(account);
        accountRoleRepository.saveAndFlush(new AccountRole(accountId, ADMIN));

        LOGGER.info("Created the local Compose bootstrap Admin; credentials were not logged.");
        return Outcome.CREATED;
    }

    public enum Outcome {
        CREATED,
        ALREADY_PROVISIONED,
        EXISTING_ACCOUNT_UNCHANGED
    }
}
