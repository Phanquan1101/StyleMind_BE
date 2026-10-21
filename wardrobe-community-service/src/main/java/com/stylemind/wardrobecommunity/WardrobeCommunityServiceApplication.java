package com.stylemind.wardrobecommunity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.stylemind.wardrobecommunity.client.commerce")
public class WardrobeCommunityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WardrobeCommunityServiceApplication.class, args);
    }
}
