package com.stylemind.wardrobecommunity.client.commerce;

import com.stylemind.wardrobecommunity.client.commerce.payload.VariantLookupItem;
import com.stylemind.wardrobecommunity.client.commerce.payload.VariantLookupRequest;
import com.stylemind.wardrobecommunity.client.commerce.payload.VariantLookupResponse;
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

/** Reads current catalog data for variant ids from Commerce; any Commerce failure becomes 503 CATALOG_UNAVAILABLE. */
@Component
public class CommerceCatalogClient {

    private static final Logger log = LoggerFactory.getLogger(CommerceCatalogClient.class);

    private final CommerceFeignClient commerce;
    private final String serviceToken;

    public CommerceCatalogClient(
            CommerceFeignClient commerce,
            @Value("${stylemind.security.service-token:}") String serviceToken) {
        this.commerce = commerce;
        // Keep startup independent of an unset token; catalog calls then fail with CATALOG_UNAVAILABLE.
        boolean valid = serviceToken.getBytes(StandardCharsets.UTF_8).length >= 32;
        this.serviceToken = valid ? serviceToken : null;
        if (!valid) {
            log.warn("STYLEMIND_SERVICE_TOKEN is missing or shorter than 32 bytes; Commerce catalog lookups will fail");
        }
    }

    /** Returns the variants Commerce knows, keyed by id; unknown ids are absent from the map. */
    public Map<UUID, VariantLookupItem> lookup(Collection<UUID> variantIds) {
        if (variantIds.isEmpty()) {
            return Map.of();
        }
        if (serviceToken == null) {
            throw unavailable();
        }
        try {
            VariantLookupResponse response = commerce.batchLookup(serviceToken,
                    new VariantLookupRequest(List.copyOf(variantIds)));
            Map<UUID, VariantLookupItem> byId = new LinkedHashMap<>();
            if (response != null && response.items() != null) {
                response.items().forEach(item -> byId.put(item.variantId(), item));
            }
            return byId;
        } catch (FeignException exception) {
            // Covers connection failures and timeouts (RetryableException) and any 4xx/5xx from Commerce.
            throw unavailable();
        }
    }

    private ApiOperationException unavailable() {
        return new ApiOperationException(HttpStatus.SERVICE_UNAVAILABLE, "CATALOG_UNAVAILABLE",
                "Product catalog is temporarily unavailable");
    }
}
