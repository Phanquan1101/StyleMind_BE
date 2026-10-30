package com.stylemind.wardrobecommunity.websocket;

import com.stylemind.wardrobecommunity.dto.chat.ChatMessageResponse;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.model.chat.ChatMessageSendResult;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatRealtimePublisher {
    public record Ack(ChatMessageResponse message, boolean replayed) { }
    public record Error(String code, String detail) { }
    private static final Logger log = LoggerFactory.getLogger(ChatRealtimePublisher.class);
    private final SimpMessagingTemplate broker;

    public void publish(ChatMessageResponse message) {
        var headers = headers(message.chatSessionId(), null);
        try { broker.convertAndSend("/topic/chats/" + message.chatSessionId(), message, headers.getMessageHeaders()); }
        catch (RuntimeException failed) { log.warn("Chat notification unavailable messageId={}", message.chatMessageId()); }
    }

    public void acknowledge(UUID actor, String socketSessionId, ChatMessageSendResult result) {
        var headers = headers(result.message().chatSessionId(), socketSessionId);
        broker.convertAndSendToUser(actor.toString(), "/queue/chats/" + result.message().chatSessionId() + "/acks",
                new Ack(result.message(), !result.created()), headers.getMessageHeaders());
    }

    public void error(UUID actor, String socketSessionId, ApiOperationException error) {
        var headers = headers(null, socketSessionId);
        broker.convertAndSendToUser(actor.toString(), "/queue/chat-errors",
                new Error(error.code(), error.detail()), headers.getMessageHeaders());
    }

    private SimpMessageHeaderAccessor headers(UUID chat, String session) {
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        if (chat != null) headers.setHeader(ChatSocketAccess.CHAT_HEADER, chat.toString());
        if (session != null) headers.setSessionId(session);
        headers.setLeaveMutable(true);
        return headers;
    }
}

