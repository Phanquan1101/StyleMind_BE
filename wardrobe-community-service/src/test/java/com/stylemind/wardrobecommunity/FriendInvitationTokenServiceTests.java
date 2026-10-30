package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stylemind.wardrobecommunity.security.FriendInvitationTokenService;
import com.stylemind.wardrobecommunity.service.friendship.internal.FriendInvitationPolicy;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FriendInvitationTokenServiceTests {

    private final FriendInvitationTokenService tokens = new FriendInvitationTokenService(new FriendInvitationPolicy());
    private final UUID invitationId = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private final Instant issuedAt = Instant.parse("2026-10-24T09:00:00Z");

    @Test
    void issuedCredentialsContainThirtyTwoRandomBytesWithoutUrlPadding() {
        var issued = tokens.issue(invitationId, issuedAt);

        assertThat(issued.value().matches("[A-Za-z0-9_-]{43}")).isTrue();
        assertThat(Base64.getUrlDecoder().decode(issued.value()).length).isEqualTo(32);
        assertThat(issued.digest().matches("[0-9a-f]{64}")).isTrue();
        assertThat(tokens.matches(invitationId, issued.value(), issued.digest())).isTrue();
    }

    @Test
    void sameCredentialCannotBeReusedForAnotherInvitation() {
        var issued = tokens.issue(invitationId, issuedAt);

        assertThat(tokens.matches(UUID.randomUUID(), issued.value(), issued.digest())).isFalse();
    }

    @Test
    void resendReplacesCredentialAndResetsSevenDaysFromResend() {
        var first = tokens.issue(invitationId, issuedAt);
        Instant resentAt = Instant.parse("2026-10-30T09:00:00Z");
        var replacement = tokens.issue(invitationId, resentAt);

        assertThat(first.expiresAt()).isEqualTo(Instant.parse("2026-10-31T09:00:00Z"));
        assertThat(replacement.expiresAt()).isEqualTo(Instant.parse("2026-11-06T09:00:00Z"));
        assertThat(tokens.matches(invitationId, first.value(), replacement.digest())).isFalse();
        assertThat(tokens.matches(invitationId, replacement.value(), replacement.digest())).isTrue();
    }

    @Test
    void changingAnyTokenCharacterInvalidatesTheCredential() {
        var issued = tokens.issue(invitationId, issuedAt);
        String changed = (issued.value().startsWith("A") ? "B" : "A") + issued.value().substring(1);

        assertThat(tokens.matches(invitationId, changed, issued.digest())).isFalse();
    }

    @Test
    void malformedWhitespacePaddedAndOversizedTokensNeverMatch() {
        var issued = tokens.issue(invitationId, issuedAt);
        for (String invalid : new String[] {null, "", " " + issued.value(), issued.value() + " ",
                issued.value() + "=", "A".repeat(42), "A".repeat(44), "é".repeat(43)}) {
            assertThat(tokens.matches(invitationId, invalid, issued.digest())).isFalse();
        }
    }

    @Test
    void missingContextAndInvalidStoredDigestsFailClosed() {
        var issued = tokens.issue(invitationId, issuedAt);

        assertThat(tokens.matches(null, issued.value(), issued.digest())).isFalse();
        for (String invalid : new String[] {null, "", "0".repeat(63), "z".repeat(64), "0".repeat(64)}) {
            assertThat(tokens.matches(invitationId, issued.value(), invalid)).isFalse();
        }
    }

    @Test
    void credentialToStringDoesNotDiscloseTokenOrDigest() {
        var issued = tokens.issue(invitationId, issuedAt);

        assertThat(issued.toString()).isEqualTo("IssuedToken[REDACTED]");
    }

    @Test
    void cannotIssueWithoutInvitationContextOrTime() {
        assertThatThrownBy(() -> tokens.issue(null, issuedAt)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> tokens.issue(invitationId, null)).isInstanceOf(NullPointerException.class);
    }
}
