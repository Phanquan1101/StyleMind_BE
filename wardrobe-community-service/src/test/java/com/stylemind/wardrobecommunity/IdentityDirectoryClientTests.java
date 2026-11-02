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

import com.stylemind.wardrobecommunity.client.identity.IdentityDirectoryClient;
import com.stylemind.wardrobecommunity.client.identity.IdentityFeignClient;
import com.stylemind.wardrobecommunity.client.identity.payload.AccountLookupItem;
import com.stylemind.wardrobecommunity.client.identity.payload.AccountLookupRequest;
import com.stylemind.wardrobecommunity.client.identity.payload.AccountLookupResponse;
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

class IdentityDirectoryClientTests {

    private static final String TOKEN = "test-only-internal-service-token-0123456789";

    private final IdentityFeignClient feign = mock(IdentityFeignClient.class);
    private final Request request = Request.create(Request.HttpMethod.POST,
            "http://identity-service:8081/internal/v1/accounts/batch-lookup", Map.of(), new byte[0],
            StandardCharsets.UTF_8, new RequestTemplate());

    @Test
    void sendsTheServiceTokenAndReturnsAccountsById() {
        UUID known = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        AccountLookupItem item = new AccountLookupItem(known, "a@example.test", "Linh", "ACTIVE");
        when(feign.batchLookup(eq(TOKEN), any())).thenReturn(new AccountLookupResponse(List.of(item)));

        Map<UUID, AccountLookupItem> found = new IdentityDirectoryClient(feign, TOKEN).lookup(List.of(known, unknown));

        assertThat(found).containsOnlyKeys(known);
        assertThat(found.get(known)).isEqualTo(item);
        verify(feign).batchLookup(TOKEN, new AccountLookupRequest(List.of(known, unknown)));
    }

    @Test
    void emptyInputDoesNotCallIdentity() {
        assertThat(new IdentityDirectoryClient(feign, TOKEN).lookup(List.of())).isEmpty();
        verifyNoInteractions(feign);
    }

    @Test
    void toStringNeverPrintsEmailOrName() {
        AccountLookupItem item = new AccountLookupItem(UUID.randomUUID(), "secret@example.test", "Secret Name", "ACTIVE");
        assertThat(item.toString()).doesNotContain("secret@example.test").doesNotContain("Secret Name");
    }

    @Test
    void connectionTimeoutsAndErrorStatusesBecomeIdentityUnavailable() {
        IdentityDirectoryClient client = new IdentityDirectoryClient(feign, TOKEN);
        List<RuntimeException> failures = List.of(
                new RetryableException(-1, "timed out", Request.HttpMethod.POST, new SocketTimeoutException(),
                        (Long) null, request),
                FeignException.errorStatus("batchLookup", response(401)),
                FeignException.errorStatus("batchLookup", response(500)));
        for (RuntimeException failure : failures) {
            doThrow(failure).when(feign).batchLookup(any(), any());
            assertThatThrownBy(() -> client.lookup(List.of(UUID.randomUUID())))
                    .isInstanceOfSatisfying(ApiOperationException.class, exception -> {
                        assertThat(exception.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(exception.code()).isEqualTo("IDENTITY_UNAVAILABLE");
                    });
        }
    }

    @Test
    void missingOrShortServiceTokenFailsClosedWithoutCallingIdentity() {
        for (String token : List.of("", "too-short")) {
            IdentityDirectoryClient client = new IdentityDirectoryClient(feign, token);
            assertThatThrownBy(() -> client.lookup(List.of(UUID.randomUUID())))
                    .isInstanceOfSatisfying(ApiOperationException.class,
                            exception -> assertThat(exception.code()).isEqualTo("IDENTITY_UNAVAILABLE"));
        }
        verify(feign, never()).batchLookup(any(), any());
    }

    private Response response(int status) {
        return Response.builder().status(status).reason("test").request(request).headers(Map.of()).build();
    }
}
