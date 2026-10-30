package com.stylemind.wardrobecommunity.service.chat.impl;

import com.stylemind.wardrobecommunity.dto.chat.ChatPageCriteria;
import com.stylemind.wardrobecommunity.dto.chat.ChatSessionListResponse;
import com.stylemind.wardrobecommunity.dto.chat.ChatSessionResponse;
import com.stylemind.wardrobecommunity.model.chat.ChatSessionOpenResult;
import com.stylemind.wardrobecommunity.model.chat.ChatSessionOpenedSnapshot;
import com.stylemind.wardrobecommunity.model.chat.ChatSessionPageSnapshot;
import com.stylemind.wardrobecommunity.model.chat.ChatSessionSnapshot;
import com.stylemind.wardrobecommunity.service.chat.ChatSessionService;
import com.stylemind.wardrobecommunity.service.chat.internal.ChatSessionPersistenceService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl implements ChatSessionService {
    private final ChatSessionPersistenceService persistence;

    @Override
    public ChatSessionOpenResult open(UUID actorId, UUID friendAccountId) {
        ChatSessionOpenedSnapshot result = persistence.open(actorId, friendAccountId);
        return new ChatSessionOpenResult(response(result.session()), result.created());
    }

    @Override
    public ChatSessionResponse get(UUID actorId, UUID chatSessionId) {
        return response(persistence.get(actorId, chatSessionId));
    }

    @Override
    public ChatSessionListResponse list(UUID actorId, ChatPageCriteria criteria) {
        ChatSessionPageSnapshot page = persistence.list(actorId, criteria);
        long pages = page.total() == 0 ? 0 : (page.total() - 1) / criteria.size() + 1;
        return new ChatSessionListResponse(page.rows().stream().map(this::response).toList(),
                criteria.page(), criteria.size(), page.total(), pages);
    }

    private ChatSessionResponse response(ChatSessionSnapshot row) {
        return new ChatSessionResponse(row.chatSessionId(), row.friendAccountId(), row.createdAt(), row.updatedAt());
    }
}

