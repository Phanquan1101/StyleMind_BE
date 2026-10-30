package com.stylemind.wardrobecommunity.service.friendship.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Approved invitation rules only; Identity verification and transactional state checks remain with callers. */
@Component
public class FriendInvitationPolicy {

    private static final Duration LIFETIME = Duration.ofDays(7);

    /** Also used on resend: the new deadline starts from the new issue time. */
    public Instant expiresAt(Instant issuedAt) {
        return Objects.requireNonNull(issuedAt, "Issue time is required").plus(LIFETIME);
    }

    /** At the exact deadline the invitation is already expired. Missing facts never grant access. */
    public boolean isPendingAndUnexpired(String status, Instant expiresAt, Instant now) {
        return "PENDING".equals(status) && expiresAt != null && now != null && now.isBefore(expiresAt);
    }

    /**
     * Email and verification must come from trusted current Identity facts, never the request body.
     * This check alone does not validate the invitation token or authorize acceptance.
     */
    public boolean matchesVerifiedRecipient(UUID senderAccountId, String recipientEmail,
            UUID authenticatedAccountId, String currentEmail, boolean emailVerified) {
        if (!emailVerified || senderAccountId == null || authenticatedAccountId == null
                || senderAccountId.equals(authenticatedAccountId) || recipientEmail == null || currentEmail == null) {
            return false;
        }
        String invited = normalize(recipientEmail);
        String current = normalize(currentEmail);
        return !invited.isEmpty() && invited.equals(current);
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
