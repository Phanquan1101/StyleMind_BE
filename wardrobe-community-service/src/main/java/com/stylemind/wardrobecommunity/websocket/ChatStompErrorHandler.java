package com.stylemind.wardrobecommunity.websocket;

import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

@Component
public class ChatStompErrorHandler extends StompSubProtocolErrorHandler {
    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable exception) {
        String code = "INVALID_REQUEST";
        Throwable cause = exception;
        for (int depth = 0; cause != null && depth < 10; depth++, cause = cause.getCause()) {
            if (cause instanceof ApiOperationException api) { code = api.code(); break; }
        }
        // Codes originate in server exceptions, never a client header/body. Do not reflect exception messages.
        byte[] body = ("{\"code\":\"" + code + "\",\"detail\":\"WebSocket request was rejected\"}").getBytes(StandardCharsets.UTF_8);
        var headers = StompHeaderAccessor.create(StompCommand.ERROR);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setMessage("WebSocket request was rejected");
        headers.setContentLength(body.length);
        return MessageBuilder.createMessage(body, headers.getMessageHeaders());
    }
}

