package com.stylemind.wardrobecommunity.repository.chat;

import com.stylemind.wardrobecommunity.entity.ChatSession;
import com.stylemind.wardrobecommunity.model.chat.ChatSessionSnapshot;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {
    String VISIBLE = """
             from ChatSession s
             join Friendship f on f.friendshipId = s.friendshipId
             join ChatParticipant lowMember on lowMember.chatSessionId = s.chatSessionId
                 and lowMember.participantSlot = 1 and lowMember.accountId = f.accountLowId
             join ChatParticipant highMember on highMember.chatSessionId = s.chatSessionId
                 and highMember.participantSlot = 2 and highMember.accountId = f.accountHighId
             where f.unfriendedAt is null and (f.accountLowId = :actorId or f.accountHighId = :actorId)
            """;
    String SNAPSHOT = """
            select new com.stylemind.wardrobecommunity.model.chat.ChatSessionSnapshot(
                s.chatSessionId,
                case when f.accountLowId = :actorId then f.accountHighId else f.accountLowId end,
                s.createdAt, s.updatedAt)
            """;

    Optional<ChatSession> findByFriendshipId(UUID friendshipId);

    /** The service must lock/check the current Friendship before taking the session write lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ChatSession> findLockedByChatSessionId(UUID chatSessionId);

    @Query(SNAPSHOT + VISIBLE + " and s.chatSessionId = :chatSessionId")
    Optional<ChatSessionSnapshot> findVisibleForAccount(
            @Param("actorId") UUID actorId, @Param("chatSessionId") UUID chatSessionId);

    @Query(SNAPSHOT + VISIBLE + " order by s.updatedAt desc, s.chatSessionId desc")
    List<ChatSessionSnapshot> findVisibleForAccount(@Param("actorId") UUID actorId, Pageable pageable);

    @Query("select count(s)" + VISIBLE)
    long countVisibleForAccount(@Param("actorId") UUID actorId);
}

