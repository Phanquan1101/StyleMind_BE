package com.stylemind.wardrobecommunity.client.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.wardrobecommunity.client.notification.payload.FriendInvitationEmailRequest;
import com.stylemind.wardrobecommunity.model.friendship.FriendInvitationDelivery;
import feign.FeignException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FriendInvitationMailClient {
    private final NotificationFeignClient notification;
    private final ObjectMapper mapper;
    private final String serviceToken;

    public FriendInvitationMailClient(NotificationFeignClient notification, ObjectMapper mapper,
            @Value("${stylemind.security.service-token:}") String serviceToken) {
        this.notification = notification;
        this.mapper = mapper;
        this.serviceToken = serviceToken.getBytes(StandardCharsets.UTF_8).length >= 32 ? serviceToken : null;
    }

    public FriendInvitationDelivery send(FriendInvitationEmailRequest request) {
        if (serviceToken == null) return FriendInvitationDelivery.UNAVAILABLE;
        try {
            var response = notification.sendFriendInvitation(serviceToken, request);
            return response != null && response.getStatusCode().value() == 202
                    ? FriendInvitationDelivery.ACCEPTED : FriendInvitationDelivery.UNKNOWN;
        } catch (FeignException failure) {
            // Accept FAILED only from the explicit Notification contract. A proxy/network failure can be uncertain.
            if (failure.status() == 503 && failure.responseBody().isPresent()) {
                try {
                    var buffer = failure.responseBody().get().asReadOnlyBuffer();
                    if (buffer.remaining() <= 16384) {
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);
                        if ("EMAIL_DELIVERY_FAILED".equals(mapper.readTree(bytes).path("code").asText())) {
                            return FriendInvitationDelivery.FAILED;
                        }
                    }
                } catch (Exception ignored) { /* Keep uncertainty; never expose provider bodies. */ }
            }
            return FriendInvitationDelivery.UNKNOWN;
        }
    }
}
