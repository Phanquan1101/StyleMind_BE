package com.stylemind.wardrobecommunity.configuration;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class FriendshipConfiguration {

    @Bean("friendshipClock")
    Clock friendshipClock() {
        return Clock.systemUTC();
    }
}
