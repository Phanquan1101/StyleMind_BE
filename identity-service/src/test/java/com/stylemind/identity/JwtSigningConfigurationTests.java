package com.stylemind.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.stylemind.identity.security.jwt.JwtSigningConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JwtSigningConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(JwtSigningConfiguration.class);

    @Test
    void missingSigningKeyFailsStartupWithClearConfigurationError() {
        contextRunner.withPropertyValues(
                "stylemind.jwt.issuer=https://identity.stylemind.local",
                "stylemind.jwt.audience=stylemind-api",
                "stylemind.jwt.private-key-path=",
                "stylemind.jwt.access-ttl=PT15M",
                "stylemind.jwt.refresh-ttl=P7D")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("STYLEMIND_JWT_PRIVATE_KEY_PATH must be configured");
                });
    }

    @Test
    void invalidTtlFailsStartupBeforeKeyIsLoaded() {
        contextRunner.withPropertyValues(
                "stylemind.jwt.issuer=https://identity.stylemind.local",
                "stylemind.jwt.audience=stylemind-api",
                "stylemind.jwt.private-key-path=key-not-read-for-invalid-ttl",
                "stylemind.jwt.access-ttl=PT0S",
                "stylemind.jwt.refresh-ttl=P7D")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("STYLEMIND_JWT_ACCESS_TTL must be a positive whole number of seconds");
                });
    }
}
