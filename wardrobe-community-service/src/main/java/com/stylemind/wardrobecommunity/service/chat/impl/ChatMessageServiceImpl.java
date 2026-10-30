package com.stylemind.wardrobecommunity.service.chat.impl;

import com.stylemind.wardrobecommunity.dto.chat.ChatHistoryCriteria;
import com.stylemind.wardrobecommunity.dto.chat.ChatHistoryResponse;
import com.stylemind.wardrobecommunity.model.chat.ChatMessageSendResult;
import com.stylemind.wardrobecommunity.model.chat.ChatTextCommand;
import com.stylemind.wardrobecommunity.service.chat.ChatMessageService;
import com.stylemind.wardrobecommunity.service.chat.internal.ChatMessagePersistenceService;
import com.stylemind.wardrobecommunity.websocket.ChatRealtimePublisher;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl implements ChatMessageService {
    private final ChatMessagePersistenceService persistence;
    private final ChatRealtimePublisher realtime;

    @Override
    public ChatMessageSendResult send(UUID actorId, UUID chatSessionId, ChatTextCommand command) {
        // The proxied method has committed before publication; notification failure cannot roll back storage.
        ChatMessageSendResult result = persistence.send(actorId, chatSessionId, command);
        if (result.created()) { realtime.publish(result.message()); }
        return result;
    }

    @Override
    public ChatHistoryResponse history(UUID actorId, UUID chatSessionId, ChatHistoryCriteria criteria) {
        return persistence.history(actorId, chatSessionId, criteria);
    }
}

