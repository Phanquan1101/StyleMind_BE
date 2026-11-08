package com.stylemind.wardrobecommunity.client.identity;

import com.stylemind.wardrobecommunity.client.identity.payload.AccountEmailVerificationItem;
import com.stylemind.wardrobecommunity.client.identity.payload.AccountLookupRequest;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import feign.FeignException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** No browser-supplied email/status and no inference from ACTIVE alone. No remote call belongs in a DB lock. */
@Component
public class IdentityEmailVerificationClient {
    private final IdentityFeignClient identity;
    private final String serviceToken;

    public IdentityEmailVerificationClient(IdentityFeignClient identity,
            @Value("${stylemind.security.service-token:}") String serviceToken) {
        this.identity = identity;
        this.serviceToken = serviceToken.getBytes(StandardCharsets.UTF_8).length >= 32 ? serviceToken : null;
    }

    public Optional<AccountEmailVerificationItem> lookup(UUID accountId) {
        if (serviceToken == null) throw unavailable();
        try {
            var response = identity.emailVerificationLookup(serviceToken, new AccountLookupRequest(List.of(accountId)));
            if (response == null || response.items() == null || response.items().size() > 1) throw unavailable();
            if (response.items().isEmpty()) return Optional.empty();
            var item = response.items().getFirst();
            if (item == null || !accountId.equals(item.accountId()) || item.email() == null
                    || item.email().isBlank() || item.accountStatus() == null || item.accountStatus().isBlank()) throw unavailable();
            return Optional.of(item);
        } catch (FeignException failed) {
            throw unavailable();
        }
    }

    public Map<UUID, AccountEmailVerificationItem> lookupMany(Collection<UUID> accountIds) {
        if (accountIds.isEmpty()) return Map.of();
        if (serviceToken == null || accountIds.size() > 100) throw unavailable();
        var requested = Set.copyOf(accountIds);
        try {
            var response = identity.emailVerificationLookup(serviceToken, new AccountLookupRequest(List.copyOf(requested)));
            if (response == null || response.items() == null || response.items().size() > requested.size()) throw unavailable();
            var result = new LinkedHashMap<UUID, AccountEmailVerificationItem>();
            for (var item : response.items()) {
                if (item == null || item.accountId() == null || !requested.contains(item.accountId())
                        || item.email() == null || item.email().isBlank() || item.accountStatus() == null
                        || item.accountStatus().isBlank() || result.putIfAbsent(item.accountId(), item) != null) throw unavailable();
            }
            return Map.copyOf(result);
        } catch (FeignException failed) { throw unavailable(); }
    }

    public Optional<AccountEmailVerificationItem> lookupByEmail(String email) {
        if (serviceToken == null) throw unavailable();
        try {
            var response = identity.emailVerificationLookupByEmail(serviceToken,
                    new com.stylemind.wardrobecommunity.client.identity.payload.AccountEmailLookupRequest(email));
            if (response == null || response.items() == null || response.items().size() > 1) throw unavailable();
            if (response.items().isEmpty()) return Optional.empty();
            var item = response.items().getFirst();
            if (item == null || item.accountId() == null || !email.equals(item.email())
                    || item.accountStatus() == null || item.accountStatus().isBlank()) throw unavailable();
            return Optional.of(item);
        } catch (FeignException failed) { throw unavailable(); }
    }

    private ApiOperationException unavailable() {
        return new ApiOperationException(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_UNAVAILABLE",
                "Account verification is temporarily unavailable");
    }
}
