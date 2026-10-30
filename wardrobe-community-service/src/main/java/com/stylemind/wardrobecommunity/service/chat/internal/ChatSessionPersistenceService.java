package com.stylemind.wardrobecommunity.service.chat.internal;

import com.stylemind.wardrobecommunity.dto.chat.ChatPageCriteria;
import com.stylemind.wardrobecommunity.entity.ChatParticipant;
import com.stylemind.wardrobecommunity.entity.ChatSession;
import com.stylemind.wardrobecommunity.entity.Friendship;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.model.chat.ChatSessionOpenedSnapshot;
import com.stylemind.wardrobecommunity.model.chat.ChatSessionPageSnapshot;
import com.stylemind.wardrobecommunity.model.chat.ChatSessionSnapshot;
import com.stylemind.wardrobecommunity.model.friendship.FriendshipPair;
import com.stylemind.wardrobecommunity.repository.chat.ChatParticipantRepository;
import com.stylemind.wardrobecommunity.repository.chat.ChatSessionRepository;
import com.stylemind.wardrobecommunity.repository.friendship.FriendshipRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatSessionPersistenceService {
    private final FriendshipRepository friendships;
    private final ChatSessionRepository sessions;
    private final ChatParticipantRepository participants;
    private final Clock clock;

    public ChatSessionPersistenceService(FriendshipRepository friendships, ChatSessionRepository sessions,
            ChatParticipantRepository participants, @Qualifier("chatClock") Clock clock) {
        this.friendships = friendships;
        this.sessions = sessions;
        this.participants = participants;
        this.clock = clock;
    }

    @Transactional
    public ChatSessionOpenedSnapshot open(UUID actorId, UUID friendAccountId) {
        if (actorId.equals(friendAccountId)) {
            throw new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request is missing or malformed");
        }
        FriendshipPair canonical = new FriendshipPair(actorId, friendAccountId);
        // Same canonical row lock as unfriend: concurrent opens and unfriend serialize across instances.
        Friendship pair = friendships.findLockedByAccountLowIdAndAccountHighId(
                canonical.accountLowId(), canonical.accountHighId())
                .filter(row -> row.getUnfriendedAt() == null).orElseThrow(ChatSessionPersistenceService::notFound);
        ChatSession existing = sessions.findByFriendshipId(pair.getFriendshipId()).orElse(null);
        if (existing != null) {
            return new ChatSessionOpenedSnapshot(
                    sessions.findVisibleForAccount(actorId, existing.getChatSessionId()).orElseThrow(ChatSessionPersistenceService::notFound),
                    false);
        }

        OffsetDateTime now = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        ChatSession session = new ChatSession();
        session.setChatSessionId(UUID.randomUUID());
        session.setFriendshipId(pair.getFriendshipId());
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        sessions.saveAndFlush(session);
        participants.saveAllAndFlush(List.of(
                participant(session.getChatSessionId(), pair.getAccountLowId(), (short) 1, now),
                participant(session.getChatSessionId(), pair.getAccountHighId(), (short) 2, now)));
        return new ChatSessionOpenedSnapshot(
                new ChatSessionSnapshot(session.getChatSessionId(), friendAccountId, now, now), true);
    }

    @Transactional(readOnly = true)
    public ChatSessionSnapshot get(UUID actorId, UUID chatSessionId) {
        return sessions.findVisibleForAccount(actorId, chatSessionId).orElseThrow(ChatSessionPersistenceService::notFound);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ChatSessionPageSnapshot list(UUID actorId, ChatPageCriteria criteria) {
        long total = sessions.countVisibleForAccount(actorId);
        List<ChatSessionSnapshot> rows = (long) criteria.page() * criteria.size() > Integer.MAX_VALUE
                ? List.of() : sessions.findVisibleForAccount(actorId, PageRequest.of(criteria.page(), criteria.size()));
        return new ChatSessionPageSnapshot(rows, total);
    }

    private ChatParticipant participant(UUID sessionId, UUID accountId, short slot, OffsetDateTime now) {
        ChatParticipant row = new ChatParticipant();
        row.setChatParticipantId(UUID.randomUUID());
        row.setChatSessionId(sessionId);
        row.setAccountId(accountId);
        row.setParticipantSlot(slot);
        row.setJoinedAt(now);
        return row;
    }

    private static ApiOperationException notFound() {
        return new ApiOperationException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found");
    }
}

