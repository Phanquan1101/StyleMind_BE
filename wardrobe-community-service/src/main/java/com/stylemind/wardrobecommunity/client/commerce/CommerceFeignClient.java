package com.stylemind.wardrobecommunity.client.commerce;

import com.stylemind.wardrobecommunity.client.commerce.payload.VariantLookupRequest;
import com.stylemind.wardrobecommunity.client.commerce.payload.VariantLookupResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "commerce-service",
        url = "${stylemind.commerce.base-url}",
        configuration = CommerceFeignConfiguration.class)
public interface CommerceFeignClient {

    String SERVICE_TOKEN_HEADER = "X-StyleMind-Service-Token";

    @PostMapping(path = "/internal/v1/variants/batch-lookup", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    VariantLookupResponse batchLookup(
            @RequestHeader(SERVICE_TOKEN_HEADER) String serviceToken,
            @RequestBody VariantLookupRequest request);
}
