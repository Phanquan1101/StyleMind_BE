package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.stylemind.wardrobecommunity.client.commerce.CommerceCatalogClient;
import com.stylemind.wardrobecommunity.client.commerce.CommerceFeignClient;
import com.stylemind.wardrobecommunity.client.commerce.payload.VariantLookupItem;
import com.stylemind.wardrobecommunity.client.commerce.payload.VariantLookupRequest;
import com.stylemind.wardrobecommunity.client.commerce.payload.VariantLookupResponse;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import feign.RetryableException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class CommerceCatalogClientTests {

    private static final String TOKEN = "test-only-internal-service-token-0123456789";

    private final CommerceFeignClient feign = mock(CommerceFeignClient.class);
    private final Request request = Request.create(Request.HttpMethod.POST,
            "http://commerce-service:8082/internal/v1/variants/batch-lookup", Map.of(), new byte[0],
            StandardCharsets.UTF_8, new RequestTemplate());

    @Test
    void sendsTheServiceTokenAndReturnsVariantsById() {
        UUID known = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        VariantLookupItem item = new VariantLookupItem(known, UUID.randomUUID(), "Shirt", "ACTIVE", "ACTIVE",
                "White", "M", 100000L, null, true);
        when(feign.batchLookup(eq(TOKEN), any())).thenReturn(new VariantLookupResponse(List.of(item)));

        Map<UUID, VariantLookupItem> found = new CommerceCatalogClient(feign, TOKEN).lookup(List.of(known, unknown));

        assertThat(found).containsOnlyKeys(known);
        assertThat(found.get(known)).isEqualTo(item);
        verify(feign).batchLookup(TOKEN, new VariantLookupRequest(List.of(known, unknown)));
    }

    @Test
    void emptyInputDoesNotCallCommerce() {
        assertThat(new CommerceCatalogClient(feign, TOKEN).lookup(List.of())).isEmpty();
        verifyNoInteractions(feign);
    }

    @Test
    void connectionTimeoutsAndErrorStatusesBecomeCatalogUnavailable() {
        CommerceCatalogClient client = new CommerceCatalogClient(feign, TOKEN);
        List<RuntimeException> failures = List.of(
                new RetryableException(-1, "timed out", Request.HttpMethod.POST, new SocketTimeoutException(),
                        (Long) null, request),
                FeignException.errorStatus("batchLookup", response(401)),
                FeignException.errorStatus("batchLookup", response(500)),
                FeignException.errorStatus("batchLookup", response(503)));
        for (RuntimeException failure : failures) {
            doThrow(failure).when(feign).batchLookup(any(), any());
            assertThatThrownBy(() -> client.lookup(List.of(UUID.randomUUID())))
                    .isInstanceOfSatisfying(ApiOperationException.class, exception -> {
                        assertThat(exception.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(exception.code()).isEqualTo("CATALOG_UNAVAILABLE");
                    });
        }
    }

    @Test
    void missingOrShortServiceTokenFailsClosedWithoutCallingCommerce() {
        for (String token : List.of("", "too-short")) {
            CommerceCatalogClient client = new CommerceCatalogClient(feign, token);
            assertThatThrownBy(() -> client.lookup(List.of(UUID.randomUUID())))
                    .isInstanceOfSatisfying(ApiOperationException.class,
                            exception -> assertThat(exception.code()).isEqualTo("CATALOG_UNAVAILABLE"));
        }
        verify(feign, never()).batchLookup(any(), any());
    }

    private Response response(int status) {
        return Response.builder().status(status).reason("test").request(request).headers(Map.of()).build();
    }
}
