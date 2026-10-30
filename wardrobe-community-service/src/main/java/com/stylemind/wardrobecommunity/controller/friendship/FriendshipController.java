package com.stylemind.wardrobecommunity.controller.friendship;

import com.stylemind.wardrobecommunity.dto.friendship.FriendInvitationListResponse;
import com.stylemind.wardrobecommunity.dto.friendship.FriendListResponse;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.security.CurrentUser;
import com.stylemind.wardrobecommunity.service.friendship.FriendshipService;
import com.stylemind.wardrobecommunity.validation.friendship.FriendshipRequestValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Manage Friends")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "INVALID_REQUEST: invalid query, UUID or self-unfriend",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class),
                        examples = @ExampleObject(value = "{\"status\":400,\"code\":\"INVALID_REQUEST\",\"detail\":\"Request is missing or malformed\"}"))),
        @ApiResponse(responseCode = "401", description = "A valid bearer JWT with UUID subject is required",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "403", description = "CUSTOMER authority is required",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
})
@RequiredArgsConstructor
public class FriendshipController {

    private final FriendshipService service;
    private final FriendshipRequestValidator validator;

    @GetMapping(path = "/friends", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List the caller's current friends", description = "Newest friendship first; current display names come from Identity. Actor is always JWT sub.",
            responses = {@ApiResponse(responseCode = "200", description = "Active friends page",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = FriendListResponse.class),
                            examples = @ExampleObject(value = """
                                    {"items":[{"friendship_id":"10000000-0000-0000-0000-000000000001",
                                    "friend_account_id":"20000000-0000-0000-0000-000000000002",
                                    "display_name":"Minh","established_at":"2026-10-24T09:00:00Z"}],
                                    "page":0,"size":20,"total_elements":1,"total_pages":1}
                                    """))),
                    @ApiResponse(responseCode = "503", description = "IDENTITY_UNAVAILABLE: account directory could not be read",
                            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))})
    @Parameters({
            @Parameter(name = "page", in = ParameterIn.QUERY, schema = @Schema(type = "integer", defaultValue = "0", minimum = "0", maximum = "2147483647")),
            @Parameter(name = "size", in = ParameterIn.QUERY, schema = @Schema(type = "integer", defaultValue = "20", minimum = "1", maximum = "100"))
    })
    public ResponseEntity<FriendListResponse> list(Authentication authentication, HttpServletRequest request) {
        UUID actor = actor(authentication);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(actor, validator.list(request)));
    }

    @GetMapping(path = "/friend-invitations", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List invitations sent by the caller", description = "Outgoing only. Shows effective expiry without writing data. Never returns invitation tokens, hashes or delivery guarantees.",
            responses = @ApiResponse(responseCode = "200", description = "Outgoing invitations page",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = FriendInvitationListResponse.class),
                            examples = @ExampleObject(value = """
                                    {"items":[{"friend_invitation_id":"30000000-0000-0000-0000-000000000003",
                                    "recipient_email":"friend@example.test","status":"PENDING",
                                    "created_at":"2026-10-24T09:00:00Z","expires_at":"2026-10-31T09:00:00Z","accepted_at":null}],
                                    "page":0,"size":20,"total_elements":1,"total_pages":1}
                                    """))))
    @Parameters({
            @Parameter(name = "page", in = ParameterIn.QUERY, schema = @Schema(type = "integer", defaultValue = "0", minimum = "0", maximum = "2147483647")),
            @Parameter(name = "size", in = ParameterIn.QUERY, schema = @Schema(type = "integer", defaultValue = "20", minimum = "1", maximum = "100"))
    })
    public ResponseEntity<FriendInvitationListResponse> outgoing(Authentication authentication, HttpServletRequest request) {
        UUID actor = actor(authentication);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.outgoingInvitations(actor, validator.list(request)));
    }

    @DeleteMapping("/friends/{friendAccountId}")
    @Operation(summary = "Unfriend the caller's peer", description = "Marks the canonical relationship inactive; keeps the row and invitation history. Does not change outfits or send email.",
            responses = {@ApiResponse(responseCode = "204", description = "Relationship marked inactive"),
                    @ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND: no active friendship for this actor and peer",
                            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<Void> unfriend(Authentication authentication,
            @Parameter(description = "Peer account UUID; never an owner override", required = true, schema = @Schema(type = "string", format = "uuid"))
            @PathVariable String friendAccountId, HttpServletRequest request) {
        UUID actor = actor(authentication);
        validator.noQuery(request);
        service.unfriend(actor, validator.friendAccountId(friendAccountId));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private UUID actor(Authentication authentication) {
        UUID subject = CurrentUser.subject(authentication);
        if (subject == null) {
            throw new ApiOperationException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
        }
        return subject;
    }
}
