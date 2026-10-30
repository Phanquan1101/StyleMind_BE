package com.stylemind.wardrobecommunity.websocket;

import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.security.CurrentUser;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatSocketInboundInterceptor implements ChannelInterceptor {
    private final JwtDecoder decoder;
    private final JwtAuthenticationConverter converter;
    private final ChatSocketSessions sessions;
    private final ChatSocketAccess access;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        try {
            return authorize(message);
        } catch (RuntimeException rejected) {
            // Do not attach the original message: framework logging must not reflect credentials or Chat text.
            throw new MessageDeliveryException(null, "WebSocket request was rejected", rejected);
        }
    }

    private Message<?> authorize(Message<?> message) {
        StompHeaderAccessor headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null) { throw denied(); }
        String sessionId = headers.getSessionId();
        StompCommand command = headers.getCommand();
        if (command == StompCommand.DISCONNECT) { sessions.remove(sessionId); return message; }
        if (command == StompCommand.UNSUBSCRIBE) { return message; }
        if (command == StompCommand.CONNECT) {
            var bearer = headers.getNativeHeader("Authorization");
            headers.removeNativeHeader("Authorization");
            if (bearer == null || bearer.size() != 1 || !bearer.getFirst().startsWith("Bearer ")) { throw unauthenticated(); }
            try {
                var jwt = decoder.decode(bearer.getFirst().substring(7));
                var auth = converter.convert(jwt);
                UUID actor = CurrentUser.subject(auth);
                if (actor == null) { throw unauthenticated(); }
                if (auth.getAuthorities().stream().noneMatch(role -> role.getAuthority().equals("ROLE_CUSTOMER"))) { throw denied(); }
                if (!sessions.register(sessionId, actor, jwt.getExpiresAt())) { throw unauthenticated(); }
                headers.setUser(new UsernamePasswordAuthenticationToken(actor.toString(), null, auth.getAuthorities()));
                // Credentials are not propagated into subsequent application/broker messages.
                return message;
            } catch (ApiOperationException exception) { throw exception; }
            catch (RuntimeException invalid) { throw unauthenticated(); }
        }
        var identity = sessions.valid(sessionId);
        if (identity == null) { throw unauthenticated(); }
        if (headers.getMessageType() == SimpMessageType.HEARTBEAT) { return message; }
        UUID chatId;
        if (command == StompCommand.SEND) {
            chatId = access.chatFromDestination(headers.getDestination(), "/app/chats/", "/messages");
        } else if (command == StompCommand.SUBSCRIBE) {
            String destination = headers.getDestination();
            if ("/user/queue/chat-errors".equals(destination)) { return message; }
            chatId = access.chatFromDestination(destination, "/topic/chats/", "");
            if (chatId == null) { chatId = access.chatFromDestination(destination, "/user/queue/chats/", "/acks"); }
        } else { throw denied(); }
        if (chatId == null) { throw denied(); }
        access.require(identity.accountId(), chatId);
        return message;
    }

    private static ApiOperationException denied() {
        return new ApiOperationException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access is denied");
    }
    private static ApiOperationException unauthenticated() {
        return new ApiOperationException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
    }
}

