package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.stylemind.wardrobecommunity.client.notification.NotificationFeignClient;
import com.stylemind.wardrobecommunity.client.notification.ShareInvitationMailClient;
import com.stylemind.wardrobecommunity.client.notification.payload.OutfitShareInvitationRequest;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShareInvitationMailClientTests {

    private static final String TOKEN = "test-only-internal-service-token-0123456789";

    private final NotificationFeignClient feign = mock(NotificationFeignClient.class);

    @Test
    void sendsTheInvitationWithTheServiceTokenAndTheFrontendOutfitUrl() {
        UUID outfitId = UUID.randomUUID();

        new ShareInvitationMailClient(feign, TOKEN, "https://app.example.test/")
                .send("friend@example.test", "Linh Tran", "Weekend look", outfitId);

        verify(feign).sendOutfitShareInvitation(TOKEN, new OutfitShareInvitationRequest("friend@example.test",
                "Linh Tran", "Weekend look", "https://app.example.test/shared/outfits/" + outfitId));
    }

    @Test
    void deliveryFailuresAreSwallowed() {
        Request request = Request.create(Request.HttpMethod.POST, "http://notification-service:8086/x", Map.of(),
                new byte[0], StandardCharsets.UTF_8, new RequestTemplate());
        Response failure = Response.builder().status(503).reason("down").request(request).headers(Map.of()).build();
        doThrow(FeignException.errorStatus("sendOutfitShareInvitation", failure)).when(feign)
                .sendOutfitShareInvitation(any(), any());
        ShareInvitationMailClient client = new ShareInvitationMailClient(feign, TOKEN, "http://localhost:3000");

        assertThatCode(() -> client.send("friend@example.test", "Linh", "Look", UUID.randomUUID()))
                .doesNotThrowAnyException();
        verify(feign).sendOutfitShareInvitation(any(), any());
    }

    @Test
    void missingOrShortServiceTokenSkipsTheInvitationWithoutFailing() {
        for (String token : new String[] {"", "too-short"}) {
            ShareInvitationMailClient client = new ShareInvitationMailClient(feign, token, "http://localhost:3000");
            assertThatCode(() -> client.send("friend@example.test", "Linh", "Look", UUID.randomUUID()))
                    .doesNotThrowAnyException();
        }
        verify(feign, never()).sendOutfitShareInvitation(any(), any());
        assertThat(new OutfitShareInvitationRequest("a@example.test", "N", "O", "http://x").toString())
                .doesNotContain("a@example.test");
    }
}
