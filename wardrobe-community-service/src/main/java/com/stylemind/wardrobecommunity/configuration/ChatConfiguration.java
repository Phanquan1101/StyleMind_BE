package com.stylemind.wardrobecommunity.configuration;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ChatConfiguration {
    @Bean("chatClock")
    Clock chatClock() {
        return Clock.systemUTC();
    }
    @Bean("chatSocketClock")
    Clock chatSocketClock() {
        return Clock.systemUTC();
    }
}

