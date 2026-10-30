package com.stylemind.wardrobecommunity.controller.chat;

import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.security.CurrentUser;
import com.stylemind.wardrobecommunity.service.chat.ChatMessageService;
import com.stylemind.wardrobecommunity.validation.chat.ChatMessageRequestValidator;
import com.stylemind.wardrobecommunity.validation.chat.ChatSessionRequestValidator;
import com.stylemind.wardrobecommunity.websocket.ChatRealtimePublisher;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatSocketController {
    private final ChatMessageService service;
    private final ChatMessageRequestValidator validator;
    private final ChatSessionRequestValidator sessions;
    private final ChatRealtimePublisher realtime;

    @MessageMapping("/chats/{chatId}/messages")
    public void send(@DestinationVariable String chatId, @Payload byte[] bytes, Principal principal,
            @Header("simpSessionId") String socketSessionId) {
        UUID actor = actor(principal);
        var result = service.send(actor, sessions.uuid(chatId), validator.send(bytes));
        realtime.acknowledge(actor, socketSessionId, result);
    }

    @MessageExceptionHandler(ApiOperationException.class)
    public void rejected(ApiOperationException exception, Principal principal, @Header("simpSessionId") String session) {
        realtime.error(actor(principal), session, exception);
    }

    @MessageExceptionHandler(Exception.class)
    public void failed(Exception exception, Principal principal, @Header("simpSessionId") String session) {
        realtime.error(actor(principal), session,
                new ApiOperationException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred"));
    }

    private UUID actor(Principal principal) {
        UUID actor = principal instanceof Authentication auth ? CurrentUser.subject(auth) : null;
        if (actor == null) throw new ApiOperationException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
        return actor;
    }
}

