package com.stylemind.wardrobecommunity.websocket;

import com.stylemind.wardrobecommunity.service.chat.internal.ChatSessionPersistenceService;
import com.stylemind.wardrobecommunity.validation.chat.ChatSessionRequestValidator;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatSocketAccess {
    public static final String CHAT_HEADER = "stylemindChatId";
    private final ChatSessionPersistenceService persistence;
    private final ChatSessionRequestValidator validator;

    public void require(UUID actorId, UUID chatId) { persistence.get(actorId, chatId); }

    public UUID chatFromDestination(String destination, String prefix, String suffix) {
        if (destination == null || !destination.startsWith(prefix) || !destination.endsWith(suffix)) { return null; }
        String id = destination.substring(prefix.length(), destination.length() - suffix.length());
        try { return validator.uuid(id); } catch (RuntimeException invalid) { return null; }
    }
}

