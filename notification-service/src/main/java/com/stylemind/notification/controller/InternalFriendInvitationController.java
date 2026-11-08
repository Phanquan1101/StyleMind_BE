package com.stylemind.notification.controller;

import com.stylemind.notification.dto.FriendInvitationEmailRequest;
import com.stylemind.notification.service.friendinvitation.FriendInvitationEmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InternalFriendInvitationController {
    private final FriendInvitationEmailService service;

    @PostMapping(path = "/internal/v1/friend-invitations", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Send a friend invitation email", responses = {
            @ApiResponse(responseCode = "202", description = "SMTP accepted; not proof of inbox delivery or friendship"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid service token"),
            @ApiResponse(responseCode = "503", description = "EMAIL_DELIVERY_FAILED"),
            @ApiResponse(responseCode = "504", description = "EMAIL_DELIVERY_UNKNOWN")
    })
    @SecurityRequirements(value = @SecurityRequirement(name = "serviceToken"))
    public ResponseEntity<Void> send(@Valid @RequestBody FriendInvitationEmailRequest request) {
        service.send(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).cacheControl(CacheControl.noStore()).build();
    }
}
