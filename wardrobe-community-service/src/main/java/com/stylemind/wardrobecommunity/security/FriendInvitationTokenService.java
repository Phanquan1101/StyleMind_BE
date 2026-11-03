package com.stylemind.wardrobecommunity.security;

import com.stylemind.wardrobecommunity.service.friendship.internal.FriendInvitationPolicy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Generates opaque invitation credentials; only the context-bound digest is suitable for storage. */
@Component
public class FriendInvitationTokenService {

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");

    private final SecureRandom random = new SecureRandom();
    private final FriendInvitationPolicy policy;

    public FriendInvitationTokenService(FriendInvitationPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "Invitation policy is required");
    }

    /** Issue again on resend, then atomically replace the stored digest and deadline in the future repository. */
    public IssuedToken issue(UUID invitationId, Instant issuedAt) {
        Objects.requireNonNull(invitationId, "Invitation ID is required");
        Instant expiresAt = policy.expiresAt(issuedAt);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new IssuedToken(value, digest(invitationId, value), expiresAt);
    }

    public String digest(UUID invitationId, String token) {
        Objects.requireNonNull(invitationId, "Invitation ID is required");
        if (token == null || !TOKEN.matcher(token).matches()) {
            throw new IllegalArgumentException("Invitation token is missing or malformed");
        }
        try {
            byte[] context = ("stylemind:friend-invitation:v1:" + invitationId + ":" + token)
                    .getBytes(StandardCharsets.US_ASCII);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(context));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** No input normalization: changing token case, adding padding or whitespace must invalidate a link. */
    public boolean matches(UUID invitationId, String token, String expectedDigest) {
        if (invitationId == null || token == null || !TOKEN.matcher(token).matches()
                || expectedDigest == null || !DIGEST.matcher(expectedDigest).matches()) {
            return false;
        }
        return MessageDigest.isEqual(digest(invitationId, token).getBytes(StandardCharsets.US_ASCII),
                expectedDigest.getBytes(StandardCharsets.US_ASCII));
    }

    /** Keep the raw value only until it is handed to Notification. Do not persist or log this object. */
    public record IssuedToken(String value, String digest, Instant expiresAt) {
        @Override
        public String toString() {
            return "IssuedToken[REDACTED]";
        }
    }
}
