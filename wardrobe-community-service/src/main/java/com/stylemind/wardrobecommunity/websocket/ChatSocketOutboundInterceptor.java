package com.stylemind.wardrobecommunity.websocket;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.OrderedMessageChannelDecorator;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatSocketOutboundInterceptor implements ExecutorChannelInterceptor {
    private final ChatSocketSessions sessions;
    private final ChatSocketAccess access;

    @Override
    public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
        if (mayReceive(message)) { return message; }
        // A dropped frame never reaches the WebSocket handler that normally advances this queue.
        Runnable next = OrderedMessageChannelDecorator.getNextMessageTask(message);
        if (next != null) { next.run(); }
        return null;
    }

    private boolean mayReceive(Message<?> message) {
        var stomp = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        // Sanitized protocol errors contain no Chat data and must reach clients rejected at CONNECT.
        if (stomp != null && stomp.getCommand() == StompCommand.ERROR) { return true; }
        if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) != SimpMessageType.MESSAGE) { return true; }
        var identity = sessions.valid(SimpMessageHeaderAccessor.getSessionId(message.getHeaders()));
        if (identity == null) { return false; }
        try {
            String chat = (String) message.getHeaders().get(ChatSocketAccess.CHAT_HEADER);
            if (chat == null) {
                String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
                return destination != null && destination.startsWith("/queue/chat-errors-user");
            }
            access.require(identity.accountId(), UUID.fromString(chat));
            return true;
        } catch (RuntimeException unavailableOrDenied) {
            // Drop a queued delivery after unfriend/expiry/DB failure; never log the private payload.
            return false;
        }
    }
}

