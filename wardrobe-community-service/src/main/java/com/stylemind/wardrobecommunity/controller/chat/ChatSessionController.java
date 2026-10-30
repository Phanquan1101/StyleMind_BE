package com.stylemind.wardrobecommunity.controller.chat;

import com.stylemind.wardrobecommunity.dto.chat.ChatSessionListResponse;
import com.stylemind.wardrobecommunity.dto.chat.ChatSessionResponse;
import com.stylemind.wardrobecommunity.dto.chat.OpenChatRequest;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.model.chat.ChatSessionOpenResult;
import com.stylemind.wardrobecommunity.security.CurrentUser;
import com.stylemind.wardrobecommunity.service.chat.ChatSessionService;
import com.stylemind.wardrobecommunity.validation.chat.ChatSessionRequestValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Chat Sessions")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "INVALID_REQUEST: malformed JSON, UUID or query; self-open",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples = @ExampleObject(value = "{\"status\":400,\"code\":\"INVALID_REQUEST\",\"detail\":\"Request is missing or malformed\"}"))),
        @ApiResponse(responseCode = "401", description = "A valid bearer JWT with UUID subject is required",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "403", description = "CUSTOMER authority is required",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND: unknown or inaccessible conversation/current friendship",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
})
public class ChatSessionController {
    private final ChatSessionService service;
    private final ChatSessionRequestValidator validator;

    @PostMapping(path = "/api/v1/me/chats", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Open the caller's conversation with a current friend",
            description = "Creates one session and both members atomically, or returns the same session unchanged. Friendship is rechecked under lock.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = OpenChatRequest.class),
                    examples = @ExampleObject(value = "{\"friend_account_id\":\"20000000-0000-0000-0000-000000000002\"}")))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Conversation created",
                    headers = @Header(name = "Location", description = "Resource path relative to the service root", schema = @Schema(type = "string")),
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ChatSessionResponse.class))),
            @ApiResponse(responseCode = "200", description = "Existing conversation reused without changing timestamps or members",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ChatSessionResponse.class))),
            @ApiResponse(responseCode = "415", description = "UNSUPPORTED_MEDIA_TYPE: application/json is required",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<ChatSessionResponse> open(Authentication authentication, HttpServletRequest request,
            @RequestBody String body) {
        UUID actorId = actor(authentication);
        validator.noQuery(request);
        ChatSessionOpenResult result = service.open(actorId, validator.open(body));
        ResponseEntity.BodyBuilder response = ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .cacheControl(CacheControl.noStore());
        if (result.created()) {
            response.location(URI.create("/api/v1/chats/" + result.session().chatSessionId()));
        }
        return response.body(result.session());
    }

    @GetMapping(path = "/api/v1/chats/{chatSessionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a conversation's safe metadata",
            description = "Requires the current active Friendship and both canonical participants. Does not return messages or image metadata.",
            responses = @ApiResponse(responseCode = "200", description = "Authorized conversation metadata",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ChatSessionResponse.class))))
    public ResponseEntity<ChatSessionResponse> get(Authentication authentication,
            @Parameter(required = true, description = "Conversation UUID", schema = @Schema(type = "string", format = "uuid"))
            @PathVariable String chatSessionId, HttpServletRequest request) {
        UUID actorId = actor(authentication);
        validator.noQuery(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.get(actorId, validator.uuid(chatSessionId)));
    }

    @GetMapping(path = "/api/v1/me/chats", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List the caller's accessible conversations",
            description = "Unfriended or invalid-member conversations are hidden. Newest updated first, with stable UUID tie breaking. Reads do not create sessions.",
            responses = @ApiResponse(responseCode = "200", description = "Authorized conversations page",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ChatSessionListResponse.class),
                            examples = @ExampleObject(value = "{\"items\":[],\"page\":0,\"size\":20,\"total_elements\":0,\"total_pages\":0}"))))
    @Parameters({
            @Parameter(name = "page", in = ParameterIn.QUERY, schema = @Schema(type = "integer", defaultValue = "0", minimum = "0", maximum = "2147483647")),
            @Parameter(name = "size", in = ParameterIn.QUERY, schema = @Schema(type = "integer", defaultValue = "20", minimum = "1", maximum = "100"))
    })
    public ResponseEntity<ChatSessionListResponse> list(Authentication authentication, HttpServletRequest request) {
        UUID actorId = actor(authentication);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(actorId, validator.list(request)));
    }

    private UUID actor(Authentication authentication) {
        UUID actorId = CurrentUser.subject(authentication);
        if (actorId == null) {
            throw new ApiOperationException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
        }
        return actorId;
    }
}

