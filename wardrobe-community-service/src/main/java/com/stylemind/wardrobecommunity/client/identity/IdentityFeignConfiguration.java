package com.stylemind.wardrobecommunity.client.identity;

import feign.Retryer;
import org.springframework.context.annotation.Bean;

/** Feign configuration scoped only to the internal Identity client. */
public class IdentityFeignConfiguration {

    @Bean
    Retryer identityRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
