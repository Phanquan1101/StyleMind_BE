package com.stylemind.wardrobecommunity.repository.friendship;

import com.stylemind.wardrobecommunity.entity.FriendInvitation;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface FriendInvitationRepository extends JpaRepository<FriendInvitation, UUID> {
    Optional<FriendInvitation> findFirstBySenderAccountIdAndRecipientEmailAndLastDeliveryAttemptAtIsNotNullOrderByLastDeliveryAttemptAtDesc(
            UUID senderAccountId, String recipientEmail);

    String INCOMING_FILTER = """
            from FriendInvitation i where i.recipientEmail = :email and i.status = 'PENDING'
              and i.expiresAt > :now and i.senderAccountId <> :actor
              and not exists (select f.friendshipId from Friendship f
                where ((f.accountLowId = :actor and f.accountHighId = i.senderAccountId)
                  or (f.accountHighId = :actor and f.accountLowId = i.senderAccountId))
                  and f.invitationsInvalidBefore is not null and i.createdAt <= f.invitationsInvalidBefore)
            """;

    @org.springframework.data.jpa.repository.Query("select i " + INCOMING_FILTER + " order by i.createdAt desc, i.friendInvitationId desc")
    List<FriendInvitation> findIncoming(
            @org.springframework.data.repository.query.Param("actor") UUID actor,
            @org.springframework.data.repository.query.Param("email") String email,
            @org.springframework.data.repository.query.Param("now") java.time.OffsetDateTime now, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("select count(i) " + INCOMING_FILTER)
    long countIncoming(@org.springframework.data.repository.query.Param("actor") UUID actor,
            @org.springframework.data.repository.query.Param("email") String email,
            @org.springframework.data.repository.query.Param("now") java.time.OffsetDateTime now);
    long countBySenderAccountId(UUID senderAccountId);

    Optional<FriendInvitation> findByFriendInvitationIdAndSenderAccountId(UUID invitationId, UUID senderAccountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FriendInvitation> findLockedByFriendInvitationIdAndSenderAccountId(UUID invitationId, UUID senderAccountId);

    /** Internal accept lookup only; callers must verify credential and current Identity recipient before accepting. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FriendInvitation> findLockedByFriendInvitationId(UUID invitationId);

    Optional<FriendInvitation> findBySenderAccountIdAndRecipientEmailAndStatus(
            UUID senderAccountId, String recipientEmail, String status);

    List<FriendInvitation> findBySenderAccountIdOrderByCreatedAtDescFriendInvitationIdDesc(
            UUID senderAccountId, Pageable pageable);

    /** Email must be the caller's verified current email from Identity, not client-supplied input. */
    List<FriendInvitation> findByRecipientEmailAndStatusOrderByCreatedAtDescFriendInvitationIdDesc(
            String recipientEmail, String status, Pageable pageable);
}
