package com.stylemind.wardrobecommunity.client.identity;

import com.stylemind.wardrobecommunity.client.identity.payload.AccountLookupRequest;
import com.stylemind.wardrobecommunity.client.identity.payload.AccountLookupResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "identity-service",
        url = "${stylemind.identity.base-url}",
        configuration = IdentityFeignConfiguration.class)
public interface IdentityFeignClient {

    @PostMapping(path = "/internal/v1/accounts/email-verification-lookup-by-email",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    com.stylemind.wardrobecommunity.client.identity.payload.AccountEmailVerificationLookupResponse emailVerificationLookupByEmail(
            @RequestHeader(SERVICE_TOKEN_HEADER) String serviceToken,
            @RequestBody com.stylemind.wardrobecommunity.client.identity.payload.AccountEmailLookupRequest request);

    @PostMapping(path = "/internal/v1/accounts/email-verification-lookup", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    com.stylemind.wardrobecommunity.client.identity.payload.AccountEmailVerificationLookupResponse emailVerificationLookup(
            @RequestHeader(SERVICE_TOKEN_HEADER) String serviceToken,
            @RequestBody AccountLookupRequest request);

    String SERVICE_TOKEN_HEADER = "X-StyleMind-Service-Token";

    @PostMapping(path = "/internal/v1/accounts/batch-lookup", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    AccountLookupResponse batchLookup(
            @RequestHeader(SERVICE_TOKEN_HEADER) String serviceToken,
            @RequestBody AccountLookupRequest request);
}
