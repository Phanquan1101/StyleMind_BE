package com.stylemind.stylist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.stylemind.stylist.client.commerce")
public class StylistServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StylistServiceApplication.class, args);
    }
}
