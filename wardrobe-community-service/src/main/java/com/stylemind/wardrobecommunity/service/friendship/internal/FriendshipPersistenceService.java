package com.stylemind.wardrobecommunity.service.friendship.internal;

import com.stylemind.wardrobecommunity.dto.friendship.FriendInvitationListResponse;
import com.stylemind.wardrobecommunity.dto.friendship.FriendInvitationSummaryResponse;
import com.stylemind.wardrobecommunity.dto.friendship.FriendPageCriteria;
import com.stylemind.wardrobecommunity.entity.FriendInvitation;
import com.stylemind.wardrobecommunity.entity.Friendship;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.model.friendship.FriendshipPageSnapshot;
import com.stylemind.wardrobecommunity.model.friendship.FriendshipPair;
import com.stylemind.wardrobecommunity.model.friendship.FriendshipSnapshot;
import com.stylemind.wardrobecommunity.repository.friendship.FriendInvitationRepository;
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
public class FriendshipPersistenceService {

    private final FriendshipRepository friendships;
    private final FriendInvitationRepository invitations;
    private final Clock clock;
    private final FriendshipMutationLock mutationLock;

    public FriendshipPersistenceService(FriendshipRepository friendships, FriendInvitationRepository invitations,
            @Qualifier("friendshipClock") Clock clock, FriendshipMutationLock mutationLock) {
        this.friendships = friendships;
        this.invitations = invitations;
        this.clock = clock;
        this.mutationLock = mutationLock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public FriendshipPageSnapshot list(UUID actorId, FriendPageCriteria criteria) {
        long total = friendships.countActiveForAccount(actorId);
        List<FriendshipSnapshot> rows = outsideJpaOffset(criteria) ? List.of()
                : friendships.findActiveForAccount(actorId, PageRequest.of(criteria.page(), criteria.size()))
                        .stream().map(row -> new FriendshipSnapshot(row.getFriendshipId(), peer(row, actorId), row.getEstablishedAt()))
                        .toList();
        return new FriendshipPageSnapshot(rows, total);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public FriendInvitationListResponse outgoingInvitations(UUID actorId, FriendPageCriteria criteria) {
        OffsetDateTime now = now();
        long total = invitations.countBySenderAccountId(actorId);
        List<FriendInvitationSummaryResponse> items = outsideJpaOffset(criteria) ? List.of()
                : invitations.findBySenderAccountIdOrderByCreatedAtDescFriendInvitationIdDesc(
                        actorId, PageRequest.of(criteria.page(), criteria.size())).stream()
                        .map(row -> invitation(row, now)).toList();
        long totalPages = total == 0 ? 0 : (total - 1) / criteria.size() + 1;
        return new FriendInvitationListResponse(items, criteria.page(), criteria.size(), total, totalPages);
    }

    @Transactional
    public void unfriend(UUID actorId, UUID friendAccountId) {
        mutationLock.acquire();
        if (actorId.equals(friendAccountId)) {
            throw new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request is missing or malformed");
        }
        FriendshipPair pair = new FriendshipPair(actorId, friendAccountId);
        Friendship row = friendships.findLockedByAccountLowIdAndAccountHighId(pair.accountLowId(), pair.accountHighId())
                .filter(candidate -> candidate.getUnfriendedAt() == null).orElseThrow(FriendshipPersistenceService::notFound);
        OffsetDateTime now = now();
        if (row.getUpdatedAt() != null && row.getUpdatedAt().isAfter(now)) now = row.getUpdatedAt();
        if (row.getEstablishedAt().isAfter(now)) now = row.getEstablishedAt();
        if (row.getInvitationsInvalidBefore() != null && row.getInvitationsInvalidBefore().isAfter(now))
            now = row.getInvitationsInvalidBefore();
        row.setUnfriendedAt(now);
        row.setInvitationsInvalidBefore(now);
        row.setUpdatedAt(now);
        friendships.saveAndFlush(row);
    }

    private UUID peer(Friendship row, UUID actorId) {
        if (actorId.equals(row.getAccountLowId())) {
            return row.getAccountHighId();
        }
        if (actorId.equals(row.getAccountHighId())) {
            return row.getAccountLowId();
        }
        throw notFound();
    }

    private FriendInvitationSummaryResponse invitation(FriendInvitation row, OffsetDateTime now) {
        String status = "PENDING".equals(row.getStatus()) && !now.isBefore(row.getExpiresAt())
                ? "EXPIRED" : row.getStatus();
        return new FriendInvitationSummaryResponse(row.getFriendInvitationId(), row.getRecipientEmail(), status,
                row.getCreatedAt(), row.getExpiresAt(), row.getAcceptedAt());
    }

    private boolean outsideJpaOffset(FriendPageCriteria criteria) {
        return (long) criteria.page() * criteria.size() > Integer.MAX_VALUE;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static ApiOperationException notFound() {
        return new ApiOperationException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found");
    }
}
