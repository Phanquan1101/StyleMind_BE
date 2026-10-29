package com.stylemind.identity.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.stylemind.identity.entity.Account;
import com.stylemind.identity.entity.AccountRole;
import com.stylemind.identity.repository.AccountRepository;
import com.stylemind.identity.repository.AccountRoleRepository;
import com.stylemind.identity.repository.EmailVerificationRepository;
import com.stylemind.identity.repository.PasswordResetRepository;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.OffsetDateTime;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("compose-local")
@Import({LocalComposeAdminBootstrapService.class, LocalComposeAdminBootstrapPostgresTests.TestBeans.class})
@Testcontainers
class LocalComposeAdminBootstrapPostgresTests {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6")
            .withDatabaseName("stylemind_identity")
            .withUsername("identity_bootstrap_test")
            .withPassword("identity_bootstrap_test_only");

    @Autowired private LocalComposeAdminBootstrapService bootstrapService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccountRoleRepository accountRoleRepository;
    @Autowired private EmailVerificationRepository emailVerificationRepository;
    @Autowired private PasswordResetRepository passwordResetRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void createsOneActiveVerifiedAdminWithHashedPasswordWithoutOtpRows() {
        String password = "local-admin-secret-2026";

        assertThat(bootstrapService.ensureAdmin("  Admin@Example.com ", password))
                .isEqualTo(LocalComposeAdminBootstrapService.Outcome.CREATED);

        Account account = accountRepository.findByEmail("admin@example.com").orElseThrow();
        assertThat(account.getAccountStatus()).isEqualTo("ACTIVE");
        assertThat(account.getEmailVerifiedAt()).isNotNull();
        assertThat(account.getRegisteredAt()).isNotNull();
        assertThat(account.getPasswordHash()).isNotEqualTo(password);
        assertThat(passwordEncoder.matches(password, account.getPasswordHash())).isTrue();
        assertThat(accountRoleRepository.findByIdAccountIdOrderByIdRoleCodeAsc(account.getAccountId()))
                .extracting(AccountRole::getRoleCode)
                .containsExactly("ADMIN");
        assertThat(emailVerificationRepository.count()).isZero();
        assertThat(passwordResetRepository.count()).isZero();

        assertThat(bootstrapService.ensureAdmin("admin@example.com", "another-local-password-2026"))
                .isEqualTo(LocalComposeAdminBootstrapService.Outcome.ALREADY_PROVISIONED);
        assertThat(accountRepository.count()).isEqualTo(1);
        assertThat(passwordEncoder.matches(password, accountRepository.findById(account.getAccountId())
                .orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void doesNotPromoteOrModifyAnExistingUnverifiedAccount() {
        Account existing = new Account();
        existing.setAccountId(java.util.UUID.randomUUID());
        existing.setEmail("admin@example.com");
        existing.setPasswordHash(passwordEncoder.encode("existing-password-2026"));
        existing.setAccountStatus("PENDING_VERIFICATION");
        existing.setEmailVerifiedAt(null);
        existing.setRegisteredAt(OffsetDateTime.now());
        accountRepository.saveAndFlush(existing);
        accountRoleRepository.saveAndFlush(new AccountRole(existing.getAccountId(), "CUSTOMER"));

        assertThat(bootstrapService.ensureAdmin("admin@example.com", "local-admin-secret-2026"))
                .isEqualTo(LocalComposeAdminBootstrapService.Outcome.EXISTING_ACCOUNT_UNCHANGED);

        Account unchanged = accountRepository.findById(existing.getAccountId()).orElseThrow();
        assertThat(unchanged.getAccountStatus()).isEqualTo("PENDING_VERIFICATION");
        assertThat(unchanged.getEmailVerifiedAt()).isNull();
        assertThat(passwordEncoder.matches("existing-password-2026", unchanged.getPasswordHash())).isTrue();
        assertThat(accountRoleRepository.findByIdAccountIdOrderByIdRoleCodeAsc(existing.getAccountId()))
                .extracting(AccountRole::getRoleCode)
                .containsExactly("CUSTOMER");
    }

    @Test
    void rejectsInvalidBootstrapCredentialsWithoutWritingRows() {
        Throwable error = org.assertj.core.api.Assertions.catchThrowable(
                () -> bootstrapService.ensureAdmin("not-an-email", "too-short"));
        assertThat(error)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configuration is invalid");
        assertThat(error.getMessage()).doesNotContain("too-short");

        assertThat(accountRepository.count()).isZero();
        assertThat(accountRoleRepository.count()).isZero();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {
        @Bean
        PasswordEncoder passwordEncoder() {
            return PasswordEncoderFactories.createDelegatingPasswordEncoder();
        }

        @Bean
        Validator validator() {
            return Validation.buildDefaultValidatorFactory().getValidator();
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
