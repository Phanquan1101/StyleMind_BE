package com.stylemind.wardrobecommunity.client.identity;

import com.stylemind.wardrobecommunity.client.identity.payload.AccountLookupItem;
import com.stylemind.wardrobecommunity.client.identity.payload.AccountLookupRequest;
import com.stylemind.wardrobecommunity.client.identity.payload.AccountLookupResponse;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import feign.FeignException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Reads account email and display name from Identity; any Identity failure becomes 503 IDENTITY_UNAVAILABLE. */
@Component
public class IdentityDirectoryClient {

    private static final Logger log = LoggerFactory.getLogger(IdentityDirectoryClient.class);

    private final IdentityFeignClient identity;
    private final String serviceToken;

    public IdentityDirectoryClient(
            IdentityFeignClient identity,
            @Value("${stylemind.security.service-token:}") String serviceToken) {
        this.identity = identity;
        // Keep startup independent of an unset token; account lookups then fail with IDENTITY_UNAVAILABLE.
        boolean valid = serviceToken.getBytes(StandardCharsets.UTF_8).length >= 32;
        this.serviceToken = valid ? serviceToken : null;
        if (!valid) {
            log.warn("STYLEMIND_SERVICE_TOKEN is missing or shorter than 32 bytes; Identity account lookups will fail");
        }
    }

    /** Returns the accounts Identity knows, keyed by id; unknown ids are absent from the map (at most 100 ids). */
    public Map<UUID, AccountLookupItem> lookup(Collection<UUID> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        if (serviceToken == null) {
            throw unavailable();
        }
        try {
            AccountLookupResponse response = identity.batchLookup(serviceToken,
                    new AccountLookupRequest(List.copyOf(accountIds)));
            Map<UUID, AccountLookupItem> byId = new LinkedHashMap<>();
            if (response != null && response.items() != null) {
                response.items().forEach(item -> byId.put(item.accountId(), item));
            }
            return byId;
        } catch (FeignException exception) {
            // Covers connection failures and timeouts (RetryableException) and any 4xx/5xx from Identity.
            throw unavailable();
        }
    }

    private ApiOperationException unavailable() {
        return new ApiOperationException(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_UNAVAILABLE",
                "Account directory is temporarily unavailable");
    }
}
