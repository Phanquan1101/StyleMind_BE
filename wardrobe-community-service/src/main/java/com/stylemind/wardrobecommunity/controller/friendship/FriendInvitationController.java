package com.stylemind.wardrobecommunity.controller.friendship;

import com.stylemind.wardrobecommunity.dto.friendship.*;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.model.friendship.InvitationActionResult;
import com.stylemind.wardrobecommunity.security.CurrentUser;
import com.stylemind.wardrobecommunity.service.friendship.FriendInvitationService;
import com.stylemind.wardrobecommunity.validation.friendship.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/friend-invitations")
@RequiredArgsConstructor
@Tag(name = "Manage Friends")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode="400",description="INVALID_REQUEST"),
    @ApiResponse(responseCode="401",description="Bearer authentication required"),
    @ApiResponse(responseCode="403",description="CUSTOMER with active verified email required"),
    @ApiResponse(responseCode="404",description="RESOURCE_NOT_FOUND: not this actor's invitation or invalid credential"),
    @ApiResponse(responseCode="409",description="Invitation already sent, used, invalidated or replaced"),
    @ApiResponse(responseCode="410",description="INVITATION_EXPIRED"),
    @ApiResponse(responseCode="429",description="INVITATION_COOLDOWN; Retry-After is the remaining part of 30 seconds"),
    @ApiResponse(responseCode="503",description="Identity, email service or invitation page unavailable"),
    @ApiResponse(responseCode="504",description="EMAIL_DELIVERY_UNKNOWN; no automatic retry")
})
public class FriendInvitationController {
    private final FriendInvitationService service;
    private final FriendInvitationRequestValidator validator;
    private final FriendshipRequestValidator common;

    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Invite by email",description="Pending already sent is returned without another email. A reverse invitation requires explicit acceptance.",
        requestBody=@io.swagger.v3.oas.annotations.parameters.RequestBody(required=true,
            content=@Content(schema=@Schema(implementation=SendFriendInvitationRequest.class))),
        responses={@ApiResponse(responseCode="201",description="Invitation created and SMTP accepted",content=@Content(schema=@Schema(implementation=FriendInvitationActionResponse.class))),
            @ApiResponse(responseCode="200",description="Existing pending, incoming invitation or friendship",content=@Content(schema=@Schema(implementation=FriendInvitationActionResponse.class)))})
    public ResponseEntity<FriendInvitationActionResponse> create(Authentication authentication,
            @RequestBody String body, HttpServletRequest request) {
        UUID actor = actor(authentication); common.noQuery(request);
        return result(service.create(actor, validator.recipient(body)));
    }

    @PostMapping(path="/{invitationId}/resend",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Retry an expired or unsuccessful invitation",description="Body {}. Sender only; at least 30 seconds. New credential and seven-day deadline. No resend for an unexpired SMTP_ACCEPTED invitation.")
    public ResponseEntity<FriendInvitationActionResponse> resend(Authentication authentication,
            @PathVariable String invitationId,@RequestBody String body,HttpServletRequest request) {
        UUID actor=actor(authentication); common.noQuery(request); validator.empty(body);
        return result(service.resend(actor,common.friendAccountId(invitationId)));
    }

    @GetMapping(path="/incoming",produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="List current incoming invitations",description="Uses the caller's verified current Identity email; excludes expired and invalidated invitations.")
    public ResponseEntity<IncomingFriendInvitationListResponse> incoming(Authentication authentication,HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.incoming(actor(authentication),common.list(request)));
    }

    @PostMapping(path="/{invitationId}/accept",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Accept from incoming invitations",description="Body {}. Explicit consent by the authenticated account whose current verified email matches the recipient; invitation ID alone grants no access.")
    public ResponseEntity<FriendInvitationActionResponse> acceptIncoming(Authentication authentication,
            @PathVariable String invitationId,@RequestBody String body,HttpServletRequest request) {
        UUID actor=actor(authentication); common.noQuery(request); validator.empty(body);
        return result(service.accept(actor,common.friendAccountId(invitationId),null));
    }

    @PostMapping(path="/accept",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Accept using an emailed credential",description="Current verified recipient is required even with a valid link. Opening an email or GET never accepts.",
        requestBody=@io.swagger.v3.oas.annotations.parameters.RequestBody(required=true,
            content=@Content(schema=@Schema(implementation=AcceptFriendInvitationRequest.class))))
    public ResponseEntity<FriendInvitationActionResponse> acceptLink(Authentication authentication,
            @RequestBody String body,HttpServletRequest request) {
        UUID actor=actor(authentication); common.noQuery(request); var command=validator.link(body);
        return result(service.accept(actor,command.friendInvitationId(),command.token()));
    }

    private ResponseEntity<FriendInvitationActionResponse> result(InvitationActionResult result) {
        return ResponseEntity.status(result.status()).cacheControl(CacheControl.noStore()).body(result.response());
    }
    private UUID actor(Authentication authentication) {
        UUID actor=CurrentUser.subject(authentication);
        if(actor==null)throw new ApiOperationException(HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED","Authentication is required");
        return actor;
    }
}
