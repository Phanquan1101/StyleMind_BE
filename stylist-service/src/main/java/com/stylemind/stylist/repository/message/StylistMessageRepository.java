package com.stylemind.stylist.repository.message;

import com.stylemind.stylist.entity.message.StylistMessageEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StylistMessageRepository extends JpaRepository<StylistMessageEntity, UUID> {

    boolean existsByStylistMessageIdAndStylistSessionIdAndSpeakerRole(
            UUID stylistMessageId, UUID stylistSessionId, String speakerRole);

    java.util.Optional<StylistMessageEntity> findByStylistMessageIdAndStylistSessionIdAndSpeakerRole(
            UUID stylistMessageId, UUID stylistSessionId, String speakerRole);

    @Query(value = """
            SELECT m.*
            FROM public.stylist_message m
            JOIN public.stylist_session s ON s.stylist_session_id = m.stylist_session_id
            WHERE s.stylist_session_id = :sessionId AND s.customer_id = :customerId
            ORDER BY m.sent_at DESC, m.stylist_message_id DESC
            LIMIT :rowLimit
            """, nativeQuery = true)
    List<StylistMessageEntity> findLatestForOwnerAndSession(
            @Param("customerId") UUID customerId,
            @Param("sessionId") UUID sessionId,
            @Param("rowLimit") int rowLimit);

    @Query(value = """
            SELECT m.*
            FROM public.stylist_message m
            JOIN public.stylist_session s ON s.stylist_session_id = m.stylist_session_id
            WHERE s.stylist_session_id = :sessionId AND s.customer_id = :customerId
              AND (m.sent_at < :beforeSentAt
                   OR (m.sent_at = :beforeSentAt AND m.stylist_message_id < :beforeMessageId))
            ORDER BY m.sent_at DESC, m.stylist_message_id DESC
            LIMIT :rowLimit
            """, nativeQuery = true)
    List<StylistMessageEntity> findOlderForOwnerAndSession(
            @Param("customerId") UUID customerId,
            @Param("sessionId") UUID sessionId,
            @Param("beforeSentAt") Instant beforeSentAt,
            @Param("beforeMessageId") UUID beforeMessageId,
            @Param("rowLimit") int rowLimit);
}
