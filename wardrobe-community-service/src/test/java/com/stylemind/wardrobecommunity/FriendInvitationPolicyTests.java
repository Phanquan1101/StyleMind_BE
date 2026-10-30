package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.assertThat;

import com.stylemind.wardrobecommunity.service.friendship.internal.FriendInvitationPolicy;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FriendInvitationPolicyTests {

    private final FriendInvitationPolicy policy = new FriendInvitationPolicy();
    private final UUID sender = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID recipient = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final Instant issuedAt = Instant.parse("2026-10-24T09:00:00Z");

    @Test
    void invitationExpiresExactlySevenDaysAfterIssue() {
        assertThat(policy.expiresAt(issuedAt)).isEqualTo(Instant.parse("2026-10-31T09:00:00Z"));
    }

    @Test
    void pendingInvitationIsUsableBeforeButNotAtOrAfterDeadline() {
        Instant deadline = policy.expiresAt(issuedAt);

        assertThat(policy.isPendingAndUnexpired("PENDING", deadline, deadline.minusNanos(1))).isTrue();
        assertThat(policy.isPendingAndUnexpired("PENDING", deadline, deadline)).isFalse();
        assertThat(policy.isPendingAndUnexpired("PENDING", deadline, deadline.plusNanos(1))).isFalse();
    }

    @Test
    void acceptedAndUnknownStatusesNeverBecomePendingAgainFromTheOldLink() {
        for (String status : new String[] {"ACCEPTED", "EXPIRED", "pending", "", null}) {
            assertThat(policy.isPendingAndUnexpired(status, policy.expiresAt(issuedAt), issuedAt)).isFalse();
        }
    }

    @Test
    void missingDeadlineOrCurrentTimeDoesNotGrantAccess() {
        assertThat(policy.isPendingAndUnexpired("PENDING", null, issuedAt)).isFalse();
        assertThat(policy.isPendingAndUnexpired("PENDING", policy.expiresAt(issuedAt), null)).isFalse();
    }

    @Test
    void verifiedRecipientMatchesNormalizedCurrentEmail() {
        assertThat(policy.matchesVerifiedRecipient(sender, " Invited@Example.Test ",
                recipient, "invited@example.test", true)).isTrue();
    }

    @Test
    void matchingUnverifiedEmailCannotAccept() {
        assertThat(policy.matchesVerifiedRecipient(sender, "invited@example.test",
                recipient, "invited@example.test", false)).isFalse();
    }

    @Test
    void forwardedInvitationDoesNotMatchAnotherCurrentEmail() {
        assertThat(policy.matchesVerifiedRecipient(sender, "invited@example.test",
                recipient, "other@example.test", true)).isFalse();
    }

    @Test
    void senderCannotAcceptTheirOwnInvitation() {
        assertThat(policy.matchesVerifiedRecipient(sender, "invited@example.test",
                sender, "invited@example.test", true)).isFalse();
    }

    @Test
    void missingOrBlankIdentityFactsDoNotMatch() {
        assertThat(policy.matchesVerifiedRecipient(null, "invited@example.test",
                recipient, "invited@example.test", true)).isFalse();
        assertThat(policy.matchesVerifiedRecipient(sender, "invited@example.test",
                null, "invited@example.test", true)).isFalse();
        assertThat(policy.matchesVerifiedRecipient(sender, null,
                recipient, "invited@example.test", true)).isFalse();
        assertThat(policy.matchesVerifiedRecipient(sender, "invited@example.test",
                recipient, null, true)).isFalse();
        assertThat(policy.matchesVerifiedRecipient(sender, "  ", recipient, "  ", true)).isFalse();
    }
}
