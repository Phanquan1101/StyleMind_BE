package com.stylemind.wardrobecommunity.service.chat;

import com.stylemind.wardrobecommunity.dto.chat.ChatHistoryCriteria;
import com.stylemind.wardrobecommunity.dto.chat.ChatHistoryResponse;
import com.stylemind.wardrobecommunity.model.chat.ChatMessageSendResult;
import com.stylemind.wardrobecommunity.model.chat.ChatTextCommand;
import java.util.UUID;

public interface ChatMessageService {
    ChatMessageSendResult send(UUID actorId, UUID chatSessionId, ChatTextCommand command);
    ChatHistoryResponse history(UUID actorId, UUID chatSessionId, ChatHistoryCriteria criteria);
}

