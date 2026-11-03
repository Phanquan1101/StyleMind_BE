package com.stylemind.wardrobecommunity.client.notification;

import com.stylemind.wardrobecommunity.client.notification.payload.OutfitShareInvitationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "notification-service",
        url = "${stylemind.notification.base-url}",
        configuration = NotificationFeignConfiguration.class)
public interface NotificationFeignClient {

    @PostMapping(path = "/internal/v1/friend-invitations", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> sendFriendInvitation(
            @RequestHeader(SERVICE_TOKEN_HEADER) String serviceToken,
            @RequestBody com.stylemind.wardrobecommunity.client.notification.payload.FriendInvitationEmailRequest request);

    String SERVICE_TOKEN_HEADER = "X-StyleMind-Service-Token";

    @PostMapping(path = "/internal/v1/outfit-share-invitations", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> sendOutfitShareInvitation(
            @RequestHeader(SERVICE_TOKEN_HEADER) String serviceToken,
            @RequestBody OutfitShareInvitationRequest request);
}
