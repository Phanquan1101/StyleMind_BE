package com.stylemind.wardrobecommunity.repository.chat;

import com.stylemind.wardrobecommunity.entity.ChatMessage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal persistence queries; the future service must authorize history and retry reads first. */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    Optional<ChatMessage> findByChatSessionIdAndSenderAccountIdAndClientMessageId(
            UUID chatSessionId, UUID senderAccountId, UUID clientMessageId);

    List<ChatMessage> findByChatSessionIdOrderBySequenceNumberDesc(UUID chatSessionId, Pageable pageable);

    List<ChatMessage> findByChatSessionIdAndSequenceNumberLessThanOrderBySequenceNumberDesc(
            UUID chatSessionId, long beforeSequence, Pageable pageable);
}

