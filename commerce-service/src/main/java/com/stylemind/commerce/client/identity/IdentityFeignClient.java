package com.stylemind.commerce.client.identity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "identity-service",
        url = "${stylemind.identity.base-url}",
        configuration = IdentityFeignConfiguration.class)
public interface IdentityFeignClient {

    @GetMapping("/api/v1/me/addresses/{addressId}")
    JsonNode getMyAddress(
            @RequestHeader("Authorization") String authorization,
            @PathVariable UUID addressId);
}
