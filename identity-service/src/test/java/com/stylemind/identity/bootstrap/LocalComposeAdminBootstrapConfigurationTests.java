package com.stylemind.identity.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

class LocalComposeAdminBootstrapConfigurationTests {

    private static final LocalComposeAdminBootstrapService SERVICE = mock(LocalComposeAdminBootstrapService.class);

    @Test
    void bootstrapRunnerRequiresComposeLocalProfileAndExplicitEnableFlag() {
        clearInvocations(SERVICE);

        runner()
                .withPropertyValues("stylemind.bootstrap-admin.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(ApplicationRunner.class));

        runner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("compose-local"))
                .withPropertyValues("stylemind.bootstrap-admin.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ApplicationRunner.class));

        runner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("compose-local"))
                .withPropertyValues(
                        "stylemind.bootstrap-admin.enabled=true",
                        "stylemind.bootstrap-admin.email=local-admin@example.test",
                        "stylemind.bootstrap-admin.password=test-only-password")
                .run(context -> {
                    assertThat(context).hasSingleBean(ApplicationRunner.class);
                    ApplicationArguments arguments = new DefaultApplicationArguments(new String[0]);
                    context.getBean(ApplicationRunner.class).run(arguments);
                });

        verify(SERVICE).ensureAdmin("local-admin@example.test", "test-only-password");
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(LocalComposeAdminBootstrapConfiguration.class, MockServiceConfiguration.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MockServiceConfiguration {
        @Bean
        @Profile("compose-local")
        LocalComposeAdminBootstrapService bootstrapService() {
            return SERVICE;
        }
    }
}
