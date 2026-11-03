package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.stylemind.wardrobecommunity.client.identity.IdentityDirectoryClient;
import com.stylemind.wardrobecommunity.client.identity.payload.AccountLookupItem;
import com.stylemind.wardrobecommunity.dto.friendship.FriendPageCriteria;
import com.stylemind.wardrobecommunity.entity.FriendInvitation;
import com.stylemind.wardrobecommunity.entity.Friendship;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.repository.friendship.FriendInvitationRepository;
import com.stylemind.wardrobecommunity.repository.friendship.FriendshipRepository;
import com.stylemind.wardrobecommunity.service.friendship.FriendshipService;
import com.stylemind.wardrobecommunity.service.friendship.impl.FriendshipServiceImpl;
import com.stylemind.wardrobecommunity.service.friendship.internal.FriendshipPersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class FriendshipServiceTests {

    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-24T09:00:00Z");

    private final FriendshipRepository friendships = mock(FriendshipRepository.class);
    private final FriendInvitationRepository invitations = mock(FriendInvitationRepository.class);
    private final IdentityDirectoryClient identity = mock(IdentityDirectoryClient.class);
    private FriendshipService service;

    @BeforeEach
    void setUp() {
        service = new FriendshipServiceImpl(new FriendshipPersistenceService(friendships, invitations,
                Clock.fixed(NOW.toInstant(), ZoneOffset.UTC),
                mock(com.stylemind.wardrobecommunity.service.friendship.internal.FriendshipMutationLock.class)), identity);
    }

    @Test
    void listUsesTheOppositeMemberAndReturnsOnlyDisplayMetadata() {
        Friendship row = friendship(A, B);
        when(friendships.countActiveForAccount(B)).thenReturn(1L);
        when(friendships.findActiveForAccount(eq(B), any())).thenReturn(List.of(row));
        when(identity.lookup(List.of(A))).thenReturn(Map.of(A,
                new AccountLookupItem(A, "private@example.test", "Linh", "ACTIVE")));

        var result = service.list(B, new FriendPageCriteria(0, 20));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().friendAccountId()).isEqualTo(A);
        assertThat(result.items().getFirst().displayName()).isEqualTo("Linh");
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);
        verify(identity).lookup(List.of(A));
    }

    @Test
    void unknownIdentityNameDoesNotDeleteOrHideTheLocalRelationship() {
        when(friendships.countActiveForAccount(A)).thenReturn(1L);
        when(friendships.findActiveForAccount(eq(A), any())).thenReturn(List.of(friendship(A, B)));
        when(identity.lookup(List.of(B))).thenReturn(Map.of());

        assertThat(service.list(A, new FriendPageCriteria(0, 20)).items().getFirst().displayName()).isNull();
        verify(friendships, never()).saveAndFlush(any());
    }

    @Test
    void emptyOrHugeOffsetPageDoesNotCallIdentityOrOverflowJpaOffset() {
        when(friendships.findActiveForAccount(eq(A), any())).thenReturn(List.of());
        assertThat(service.list(A, new FriendPageCriteria(0, 20)).items()).isEmpty();
        when(friendships.countActiveForAccount(B)).thenReturn(5L);

        var result = service.list(B, new FriendPageCriteria(Integer.MAX_VALUE, 100));

        assertThat(result.items()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(5);
        assertThat(result.totalPages()).isEqualTo(1);
        verify(friendships, never()).findActiveForAccount(eq(B), any());
        verifyNoInteractions(identity);
    }

    @Test
    void aForeignRowFailsClosedBeforeAnyIdentityLookup() {
        when(friendships.findActiveForAccount(eq(C), any())).thenReturn(List.of(friendship(A, B)));

        assertThatThrownBy(() -> service.list(C, new FriendPageCriteria(0, 20)))
                .isInstanceOfSatisfying(ApiOperationException.class, exception -> assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(identity);
    }

    @Test
    void outgoingListShowsExpiryAtDeadlineWithoutWritingOrCallingIdentity() {
        FriendInvitation row = new FriendInvitation();
        row.setFriendInvitationId(UUID.randomUUID());
        row.setRecipientEmail("invited@example.test");
        row.setStatus("PENDING");
        row.setCreatedAt(NOW.minusDays(7));
        row.setExpiresAt(NOW);
        when(invitations.countBySenderAccountId(A)).thenReturn(1L);
        when(invitations.findBySenderAccountIdOrderByCreatedAtDescFriendInvitationIdDesc(eq(A), any())).thenReturn(List.of(row));

        assertThat(service.outgoingInvitations(A, new FriendPageCriteria(0, 20)).items().getFirst().status()).isEqualTo("EXPIRED");
        assertThat(row.getStatus()).isEqualTo("PENDING");
        verify(invitations, never()).saveAndFlush(any());
        verifyNoInteractions(identity);
    }

    @Test
    void unfriendKeepsPairIdentityAndUsesMicrosecondServerTime() {
        Friendship row = friendship(A, B);
        UUID id = row.getFriendshipId();
        when(friendships.findLockedByAccountLowIdAndAccountHighId(A, B)).thenReturn(Optional.of(row));

        service.unfriend(B, A);

        assertThat(row.getFriendshipId()).isEqualTo(id);
        assertThat(row.getUnfriendedAt()).isEqualTo(NOW);
        assertThat(row.getUpdatedAt()).isEqualTo(NOW);
        verify(friendships).saveAndFlush(row);
        verifyNoInteractions(identity, invitations);
    }

    @Test
    void missingOrInactiveFriendshipUsesTheSameNotFoundResponse() {
        when(friendships.findLockedByAccountLowIdAndAccountHighId(A, B)).thenReturn(Optional.empty());
        notFound(() -> service.unfriend(A, B));
        Friendship inactive = friendship(A, B);
        inactive.setUnfriendedAt(NOW.minusSeconds(1));
        when(friendships.findLockedByAccountLowIdAndAccountHighId(A, B)).thenReturn(Optional.of(inactive));
        notFound(() -> service.unfriend(A, B));
        verify(friendships, never()).saveAndFlush(any());
    }

    @Test
    void selfUnfriendDoesNotTouchPersistence() {
        assertThatThrownBy(() -> service.unfriend(A, A)).isInstanceOfSatisfying(ApiOperationException.class,
                exception -> assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(friendships, invitations, identity);
    }

    private Friendship friendship(UUID low, UUID high) {
        Friendship row = new Friendship();
        row.setFriendshipId(UUID.randomUUID());
        row.setAccountLowId(low);
        row.setAccountHighId(high);
        row.setEstablishedAt(OffsetDateTime.ofInstant(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC));
        return row;
    }

    private void notFound(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiOperationException.class, exception -> {
            assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(exception.code()).isEqualTo("RESOURCE_NOT_FOUND");
        });
    }
}
