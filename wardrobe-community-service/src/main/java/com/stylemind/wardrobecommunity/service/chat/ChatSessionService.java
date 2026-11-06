package com.stylemind.wardrobecommunity.service.chat;

import com.stylemind.wardrobecommunity.dto.chat.ChatPageCriteria;
import com.stylemind.wardrobecommunity.dto.chat.ChatSessionListResponse;
import com.stylemind.wardrobecommunity.dto.chat.ChatSessionResponse;
import com.stylemind.wardrobecommunity.model.chat.ChatSessionOpenResult;
import java.util.UUID;

public interface ChatSessionService {
    ChatSessionOpenResult open(UUID actorId, UUID friendAccountId);
    ChatSessionResponse get(UUID actorId, UUID chatSessionId);
    ChatSessionListResponse list(UUID actorId, ChatPageCriteria criteria);
}

