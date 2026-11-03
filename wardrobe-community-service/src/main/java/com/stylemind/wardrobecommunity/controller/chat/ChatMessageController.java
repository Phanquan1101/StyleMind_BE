package com.stylemind.wardrobecommunity.controller.chat;

import com.stylemind.wardrobecommunity.dto.chat.ChatHistoryResponse;
import com.stylemind.wardrobecommunity.dto.chat.ChatMessageResponse;
import com.stylemind.wardrobecommunity.dto.chat.SendChatTextRequest;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.security.CurrentUser;
import com.stylemind.wardrobecommunity.service.chat.ChatMessageService;
import com.stylemind.wardrobecommunity.validation.chat.ChatMessageRequestValidator;
import com.stylemind.wardrobecommunity.validation.chat.ChatSessionRequestValidator;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chats/{chatId}/messages")
@Tag(name = "Chat Messages")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "INVALID_REQUEST: malformed input, text or cursor",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class), mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
        @ApiResponse(responseCode = "401", description = "JWT with UUID subject required"),
        @ApiResponse(responseCode = "403", description = "CUSTOMER required"),
        @ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND: inaccessible current conversation")
})
public class ChatMessageController {
    private final ChatMessageService service;
    private final ChatMessageRequestValidator validator;
    private final ChatSessionRequestValidator sessions;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Send a text message or retry the same client key",
            description = "Stores before realtime publication. This JSON media type sends text; use multipart/form-data on the same route for private images.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(schema = @Schema(implementation = SendChatTextRequest.class),
                    examples = @ExampleObject(value = "{\"client_message_id\":\"40000000-0000-0000-0000-000000000004\",\"content\":\"Chào bạn\"}")))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Message committed", content = @Content(schema = @Schema(implementation = ChatMessageResponse.class))),
            @ApiResponse(responseCode = "200", description = "Identical retry; original message returned", content = @Content(schema = @Schema(implementation = ChatMessageResponse.class))),
            @ApiResponse(responseCode = "409", description = "IDEMPOTENCY_CONFLICT or CHAT_SEQUENCE_EXHAUSTED"),
            @ApiResponse(responseCode = "415", description = "application/json required")
    })
    public ResponseEntity<ChatMessageResponse> send(Authentication auth, @PathVariable String chatId,
            HttpServletRequest request, @RequestBody byte[] body) {
        UUID actor = actor(auth);
        sessions.noQuery(request);
        var result = service.send(actor, sessions.uuid(chatId), validator.send(body));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .cacheControl(CacheControl.noStore()).body(result.message());
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Read a cursor window of authorized chat history",
            description = "Exclusive sequence cursor. Items are chronological within the window; no object keys or cloud URLs.")
    @Parameters({
            @Parameter(name = "before_sequence", in = ParameterIn.QUERY, schema = @Schema(type = "integer", format = "int64", minimum = "1")),
            @Parameter(name = "limit", in = ParameterIn.QUERY, schema = @Schema(type = "integer", defaultValue = "30", minimum = "1", maximum = "100"))
    })
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ChatHistoryResponse.class)))
    public ResponseEntity<ChatHistoryResponse> history(Authentication auth, @PathVariable String chatId, HttpServletRequest request) {
        UUID actor = actor(auth);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.history(actor, sessions.uuid(chatId), validator.history(request)));
    }

    private UUID actor(Authentication auth) {
        UUID actor = CurrentUser.subject(auth);
        if (actor == null) { throw new ApiOperationException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required"); }
        return actor;
    }
}

