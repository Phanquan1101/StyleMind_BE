package com.stylemind.wardrobecommunity.client.commerce;

import feign.Retryer;
import org.springframework.context.annotation.Bean;

/** Feign configuration scoped only to the internal Commerce client. */
public class CommerceFeignConfiguration {

    @Bean
    Retryer commerceRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
