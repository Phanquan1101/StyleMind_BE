package com.stylemind.stylist.client.commerce;

import feign.Request;
import feign.Retryer;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/** Per-client settings; deliberately not component-scanned as global Feign configuration. */
public class CommerceFeignConfiguration {

    @Bean
    Request.Options commerceRequestOptions(
            @Value("${stylemind.clients.commerce.connect-timeout-ms:1000}") int connectTimeoutMs,
            @Value("${stylemind.clients.commerce.read-timeout-ms:2000}") int readTimeoutMs) {
        if (connectTimeoutMs < 1 || readTimeoutMs < 1) {
            throw new IllegalStateException("Commerce client timeouts must be positive");
        }
        return new Request.Options(connectTimeoutMs, TimeUnit.MILLISECONDS,
                readTimeoutMs, TimeUnit.MILLISECONDS, false);
    }

    @Bean
    Retryer commerceRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
