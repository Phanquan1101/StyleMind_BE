package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.wardrobecommunity.client.notification.*;
import com.stylemind.wardrobecommunity.client.notification.payload.FriendInvitationEmailRequest;
import com.stylemind.wardrobecommunity.model.friendship.FriendInvitationDelivery;
import feign.*;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class FriendInvitationMailClientTests {
    static final String TOKEN = "test-only-service-token-0123456789abcdef";
    final NotificationFeignClient feign = mock(NotificationFeignClient.class);
    final FriendInvitationMailClient client = new FriendInvitationMailClient(feign, new ObjectMapper(), TOKEN);
    final FriendInvitationEmailRequest input = new FriendInvitationEmailRequest("private@example.test", "Linh",
            "https://app.example.test/invitation#test-only-token", OffsetDateTime.parse("2026-11-04T00:00:00Z"));

    @Test void only202MeansSmtpAccepted() {
        when(feign.sendFriendInvitation(TOKEN, input)).thenReturn(ResponseEntity.accepted().build());
        assertThat(client.send(input)).isEqualTo(FriendInvitationDelivery.ACCEPTED);
        verify(feign).sendFriendInvitation(TOKEN, input);
        when(feign.sendFriendInvitation(TOKEN, input)).thenReturn(ResponseEntity.ok().build());
        assertThat(client.send(input)).isEqualTo(FriendInvitationDelivery.UNKNOWN);
    }

    @Test void explicitSmtpFailureIsNotSwallowedAsSuccess() {
        when(feign.sendFriendInvitation(any(), any())).thenThrow(failure(503, "{\"code\":\"EMAIL_DELIVERY_FAILED\"}"));
        assertThat(client.send(input)).isEqualTo(FriendInvitationDelivery.FAILED);
    }

    @Test void timeoutOtherErrorsAndMalformedReplyRemainUnknownWithoutRetry() {
        for (var error : new FeignException[]{failure(504, "{\"code\":\"EMAIL_DELIVERY_UNKNOWN\"}"),
                failure(503, "{bad"), failure(503, "{\"code\":\"OTHER\"}"), failure(401, "{}"), failure(502, "{}")}) {
            reset(feign);
            when(feign.sendFriendInvitation(any(), any())).thenThrow(error);
            assertThat(client.send(input)).isEqualTo(FriendInvitationDelivery.UNKNOWN);
            verify(feign, times(1)).sendFriendInvitation(any(), any());
        }
    }

    @Test void networkTimeoutIsUnknown() {
        when(feign.sendFriendInvitation(any(), any())).thenThrow(mock(RetryableException.class));
        assertThat(client.send(input)).isEqualTo(FriendInvitationDelivery.UNKNOWN);
    }

    @Test void missingConfigDoesNotAttemptDelivery() {
        assertThat(new FriendInvitationMailClient(feign, new ObjectMapper(), "").send(input))
                .isEqualTo(FriendInvitationDelivery.UNAVAILABLE);
        verifyNoInteractions(feign);
    }

    @Test void diagnosticStringNeverExposesEmailOrLink() {
        assertThat(input.toString()).doesNotContain("private@example.test", "test-only-token", "https://");
    }

    private FeignException failure(int status, String body) {
        Request req = Request.create(Request.HttpMethod.POST, "http://notification-test/internal", Map.of(),
                new byte[0], StandardCharsets.UTF_8, new RequestTemplate());
        return FeignException.errorStatus("sendFriendInvitation", Response.builder().request(req).status(status)
                .headers(Map.of()).body(body, StandardCharsets.UTF_8).build());
    }
}
