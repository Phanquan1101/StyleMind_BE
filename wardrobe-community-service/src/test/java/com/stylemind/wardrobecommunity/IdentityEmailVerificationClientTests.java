package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.stylemind.wardrobecommunity.client.identity.*;
import com.stylemind.wardrobecommunity.client.identity.payload.*;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentityEmailVerificationClientTests {
    static final String TOKEN = "test-only-service-token-0123456789abcdef";
    final IdentityFeignClient feign = mock(IdentityFeignClient.class);
    final UUID actor = UUID.randomUUID();
    final IdentityEmailVerificationClient client = new IdentityEmailVerificationClient(feign, TOKEN);

    @Test void consumesExplicitTimestampAndActiveStatusWithoutInferringFromActive() {
        for (var item : List.of(facts("ACTIVE", null), facts("SUSPENDED", OffsetDateTime.now()),
                facts("ACTIVE", OffsetDateTime.now()))) {
            when(feign.emailVerificationLookup(any(), any())).thenReturn(new AccountEmailVerificationLookupResponse(List.of(item)));
            assertThat(client.lookup(actor)).contains(item);
            assertThat(item.activeAndEmailVerified()).isEqualTo(item.emailVerifiedAt() != null && item.accountStatus().equals("ACTIVE"));
        }
        verify(feign, times(3)).emailVerificationLookup(TOKEN, new AccountLookupRequest(List.of(actor)));
    }

    @Test void unknownAccountIsAbsentAndDoesNotInventVerification() {
        when(feign.emailVerificationLookup(any(), any())).thenReturn(new AccountEmailVerificationLookupResponse(List.of()));
        assertThat(client.lookup(actor)).isEmpty();
    }

    @Test void missingConfigFailsClosedWithoutCallingIdentity() {
        assertThatThrownBy(() -> new IdentityEmailVerificationClient(feign, "short").lookup(actor))
                .isInstanceOf(ApiOperationException.class);
        verifyNoInteractions(feign);
    }

    @Test void malformedOrWrongAccountResponseNeverGrantsFacts() {
        var wrong = new AccountEmailVerificationItem(UUID.randomUUID(), "x@example.test", "ACTIVE", OffsetDateTime.now());
        for (var response : new AccountEmailVerificationLookupResponse[]{
                null, new AccountEmailVerificationLookupResponse(null),
                new AccountEmailVerificationLookupResponse(List.of(wrong)),
                new AccountEmailVerificationLookupResponse(List.of(facts("ACTIVE", null), facts("ACTIVE", null))),
                new AccountEmailVerificationLookupResponse(List.of(new AccountEmailVerificationItem(actor, null, "ACTIVE", OffsetDateTime.now())))}) {
            when(feign.emailVerificationLookup(any(), any())).thenReturn(response);
            assertThatThrownBy(() -> client.lookup(actor)).isInstanceOf(ApiOperationException.class);
        }
    }

    @Test void remoteFailureIsSanitized() {
        when(feign.emailVerificationLookup(any(), any())).thenThrow(mock(feign.RetryableException.class));
        assertThatThrownBy(() -> client.lookup(actor)).isInstanceOf(ApiOperationException.class)
                .hasMessage("IDENTITY_UNAVAILABLE").hasNoCause();
    }

    @Test void diagnosticsRedactCurrentEmail() {
        assertThat(facts("ACTIVE", null).toString()).doesNotContain("private@example.test");
    }

    private AccountEmailVerificationItem facts(String status, OffsetDateTime at) {
        return new AccountEmailVerificationItem(actor, "private@example.test", status, at);
    }

    @Test void emailLookupChecksTheExactNormalizedEmailAndHandlesUnknown() {
        var item=facts("ACTIVE",OffsetDateTime.now());
        when(feign.emailVerificationLookupByEmail(any(),any())).thenReturn(new AccountEmailVerificationLookupResponse(List.of(item)));
        assertThat(client.lookupByEmail("private@example.test")).contains(item);
        verify(feign).emailVerificationLookupByEmail(TOKEN,new AccountEmailLookupRequest("private@example.test"));
        assertThatThrownBy(()->client.lookupByEmail("different@example.test")).isInstanceOf(ApiOperationException.class);
        when(feign.emailVerificationLookupByEmail(any(),any())).thenReturn(new AccountEmailVerificationLookupResponse(List.of()));
        assertThat(client.lookupByEmail("unknown@example.test")).isEmpty();
    }

    @Test void emailLookupFailsClosedForMalformedResponseAndRemoteErrors() {
        for(var response:new AccountEmailVerificationLookupResponse[]{null,new AccountEmailVerificationLookupResponse(null),
            new AccountEmailVerificationLookupResponse(List.of(new AccountEmailVerificationItem(null,"private@example.test","ACTIVE",null)))}) {
            when(feign.emailVerificationLookupByEmail(any(),any())).thenReturn(response);
            assertThatThrownBy(()->client.lookupByEmail("private@example.test")).isInstanceOf(ApiOperationException.class);
        }
        when(feign.emailVerificationLookupByEmail(any(),any())).thenThrow(mock(feign.RetryableException.class));
        assertThatThrownBy(()->client.lookupByEmail("private@example.test")).hasMessage("IDENTITY_UNAVAILABLE").hasNoCause();
    }
}
