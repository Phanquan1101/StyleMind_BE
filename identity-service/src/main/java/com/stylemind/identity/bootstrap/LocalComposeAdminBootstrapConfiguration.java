package com.stylemind.identity.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("compose-local")
@ConditionalOnProperty(prefix = "stylemind.bootstrap-admin", name = "enabled", havingValue = "true")
public class LocalComposeAdminBootstrapConfiguration {

    @Bean
    ApplicationRunner localComposeAdminBootstrap(
            LocalComposeAdminBootstrapService bootstrapService,
            @Value("${stylemind.bootstrap-admin.email:}") String email,
            @Value("${stylemind.bootstrap-admin.password:}") String password) {
        return args -> bootstrapService.ensureAdmin(email, password);
    }
}
