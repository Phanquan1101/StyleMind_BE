package com.stylemind.wardrobecommunity.service.friendship.impl;

import com.stylemind.wardrobecommunity.client.identity.IdentityDirectoryClient;
import com.stylemind.wardrobecommunity.client.identity.IdentityEmailVerificationClient;
import com.stylemind.wardrobecommunity.client.identity.payload.AccountLookupItem;
import com.stylemind.wardrobecommunity.dto.friendship.FriendInvitationListResponse;
import com.stylemind.wardrobecommunity.dto.friendship.FriendListResponse;
import com.stylemind.wardrobecommunity.dto.friendship.FriendPageCriteria;
import com.stylemind.wardrobecommunity.dto.friendship.FriendSummaryResponse;
import com.stylemind.wardrobecommunity.model.friendship.FriendshipPageSnapshot;
import com.stylemind.wardrobecommunity.model.friendship.FriendshipSnapshot;
import com.stylemind.wardrobecommunity.service.friendship.FriendshipService;
import com.stylemind.wardrobecommunity.service.friendship.internal.FriendshipPersistenceService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class FriendshipServiceImpl implements FriendshipService {

    private final FriendshipPersistenceService persistence;
    private final IdentityDirectoryClient identity;
    private final IdentityEmailVerificationClient verification;

    public FriendshipServiceImpl(FriendshipPersistenceService persistence, IdentityDirectoryClient identity,
            IdentityEmailVerificationClient verification) {
        this.persistence = persistence;
        this.identity = identity;
        this.verification = verification;
    }

    @Override
    public FriendListResponse list(UUID actorId, FriendPageCriteria criteria) {
        FriendshipPageSnapshot snapshot = persistence.list(actorId, criteria);
        // The persistence bean's transaction has completed before the Identity HTTP call.
        Map<UUID, AccountLookupItem> accounts = snapshot.rows().isEmpty() ? Map.of()
                : identity.lookup(snapshot.rows().stream().map(FriendshipSnapshot::peerId).toList());
        var missingIds = snapshot.rows().stream().map(FriendshipSnapshot::peerId)
                .filter(id -> !accounts.containsKey(id) || accounts.get(id).email() == null || accounts.get(id).email().isBlank()).toList();
        var facts = missingIds.isEmpty() ? Map.<UUID, com.stylemind.wardrobecommunity.client.identity.payload.AccountEmailVerificationItem>of()
                : verification.lookupMany(missingIds);
        List<FriendSummaryResponse> items = snapshot.rows().stream().map(row -> {
            AccountLookupItem account = accounts.get(row.peerId());
            var fallback = facts.get(row.peerId());
            String email = account != null && account.email() != null && !account.email().isBlank()
                    ? account.email() : fallback == null ? null : fallback.email();
            return new FriendSummaryResponse(row.friendshipId(), row.peerId(),
                    email, account == null ? null : account.displayName(), row.establishedAt());
        }).toList();
        long totalPages = snapshot.total() == 0 ? 0 : (snapshot.total() - 1) / criteria.size() + 1;
        return new FriendListResponse(items, criteria.page(), criteria.size(), snapshot.total(), totalPages);
    }

    @Override
    public FriendInvitationListResponse outgoingInvitations(UUID actorId, FriendPageCriteria criteria) {
        return persistence.outgoingInvitations(actorId, criteria);
    }

    @Override
    public void unfriend(UUID actorId, UUID friendAccountId) {
        persistence.unfriend(actorId, friendAccountId);
    }
}
