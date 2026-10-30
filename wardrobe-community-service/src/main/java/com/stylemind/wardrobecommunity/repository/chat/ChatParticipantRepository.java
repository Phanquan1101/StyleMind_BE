package com.stylemind.wardrobecommunity.repository.chat;

import com.stylemind.wardrobecommunity.entity.ChatParticipant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, UUID> {
    List<ChatParticipant> findByChatSessionIdOrderByParticipantSlotAsc(UUID chatSessionId);

    /** Membership alone is insufficient: callers also need the current active Friendship. */
    Optional<ChatParticipant> findByChatSessionIdAndAccountId(UUID chatSessionId, UUID accountId);
}

