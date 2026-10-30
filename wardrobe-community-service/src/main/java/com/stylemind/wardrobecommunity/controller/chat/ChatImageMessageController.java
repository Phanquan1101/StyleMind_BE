package com.stylemind.wardrobecommunity.controller.chat;

import com.stylemind.wardrobecommunity.dto.chat.ChatMessageResponse;
import com.stylemind.wardrobecommunity.dto.chat.SendChatImagesRequest;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.security.CurrentUser;
import com.stylemind.wardrobecommunity.service.chat.ChatImageMessageService;
import com.stylemind.wardrobecommunity.service.chat.ChatSessionService;
import com.stylemind.wardrobecommunity.validation.chat.ChatImageRequestValidator;
import com.stylemind.wardrobecommunity.validation.chat.ChatSessionRequestValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name="Chat Images")
@SecurityRequirement(name="bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode="400",description="INVALID_REQUEST: malformed metadata/file/format or image exceeds 40 megapixels"),
    @ApiResponse(responseCode="401",description="JWT UUID subject required"),
    @ApiResponse(responseCode="403",description="CUSTOMER required"),
    @ApiResponse(responseCode="404",description="RESOURCE_NOT_FOUND: missing/inaccessible chat or image"),
    @ApiResponse(responseCode="503",description="Private storage unavailable or image processor busy")
})
public class ChatImageMessageController {
    private final ChatImageMessageService images;
    private final ChatSessionService chats;
    private final ChatSessionRequestValidator ids;
    private final ChatImageRequestValidator validator;

    @PostMapping(path="/api/v1/chats/{chatId}/messages",consumes=MediaType.MULTIPART_FORM_DATA_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Send 1–5 private chat images with an optional caption",
            description="Each image <= 5 MiB and <= 40 megapixels; JPEG/PNG/WebP bytes validated. Same sender/client key shares text API idempotency.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required=true,content=@Content(
            mediaType=MediaType.MULTIPART_FORM_DATA_VALUE,schema=@Schema(implementation=SendChatImagesRequest.class),
            encoding=@Encoding(name="message",contentType=MediaType.APPLICATION_JSON_VALUE)))
    @ApiResponses({
        @ApiResponse(responseCode="201",description="Message and all metadata committed",content=@Content(schema=@Schema(implementation=ChatMessageResponse.class))),
        @ApiResponse(responseCode="200",description="Identical ordered payload retry",content=@Content(schema=@Schema(implementation=ChatMessageResponse.class))),
        @ApiResponse(responseCode="409",description="IDEMPOTENCY_CONFLICT or CHAT_SEQUENCE_EXHAUSTED"),
        @ApiResponse(responseCode="413",description="CHAT_IMAGE_TOO_LARGE: multipart limits exceeded")
    })
    public ResponseEntity<ChatMessageResponse> send(Authentication authentication,@PathVariable String chatId,HttpServletRequest request) {
        UUID actor=actor(authentication),chat=ids.uuid(chatId);
        chats.get(actor,chat);
        var result=images.send(actor,chat,validator.validate(request));
        return ResponseEntity.status(result.created()?HttpStatus.CREATED:HttpStatus.OK).cacheControl(CacheControl.noStore()).body(result.message());
    }

    @GetMapping("/api/v1/chats/{chatId}/attachments/{attachmentId}/content")
    @Operation(summary="Read private image bytes under current chat authorization",
            description="Backend returns bounded private image bytes; no public URL/redirect. Access is rechecked after the storage read.")
    @ApiResponse(responseCode="200",description="Validated image bytes",content={
        @Content(mediaType="image/jpeg",schema=@Schema(type="string",format="binary")),
        @Content(mediaType="image/png",schema=@Schema(type="string",format="binary")),
        @Content(mediaType="image/webp",schema=@Schema(type="string",format="binary"))
    })
    public ResponseEntity<byte[]> read(Authentication authentication,@PathVariable String chatId,
            @PathVariable String attachmentId,HttpServletRequest request) {
        ids.noQuery(request);
        var data=images.read(actor(authentication),ids.uuid(chatId),ids.uuid(attachmentId));
        String extension=switch(data.contentType()){case "image/jpeg"->"jpg";case "image/png"->"png";case "image/webp"->"webp";default->throw new IllegalStateException("Validated image MIME required");};
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
                .header("Content-Disposition",org.springframework.http.ContentDisposition.inline().filename("chat-image-"+attachmentId+"."+extension).build().toString())
                .contentType(MediaType.parseMediaType(data.contentType())).contentLength(data.bytes().length).body(data.bytes());
    }
    private UUID actor(Authentication authentication) {
        UUID actor=CurrentUser.subject(authentication);
        if(actor==null) throw new ApiOperationException(HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED","Authentication is required");
        return actor;
    }
}
