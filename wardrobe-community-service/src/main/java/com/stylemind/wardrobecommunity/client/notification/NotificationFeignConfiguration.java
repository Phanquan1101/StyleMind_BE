package com.stylemind.wardrobecommunity.client.notification;

import feign.Retryer;
import org.springframework.context.annotation.Bean;

/** Feign configuration scoped only to the internal Notification client. */
public class NotificationFeignConfiguration {

    @Bean
    Retryer notificationRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
