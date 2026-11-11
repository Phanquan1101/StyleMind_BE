package com.stylemind.wardrobecommunity.errors;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class FriendInvitationFailure extends RuntimeException {
    private final HttpStatus status;
    private final long retryAfter;
    private final UUID invitationId;
    private final String recipientHint;
    public FriendInvitationFailure(HttpStatus status, String code, long retryAfter, UUID invitationId) {
        this(status, code, retryAfter, invitationId, null);
    }
    /** @param recipientHint masked address of the account the invitation was meant for (only given when the link credential is valid) */
    public FriendInvitationFailure(HttpStatus status, String code, long retryAfter, UUID invitationId, String recipientHint) {
        super(code); this.status = status; this.retryAfter = retryAfter; this.invitationId = invitationId; this.recipientHint = recipientHint;
    }
    public HttpStatus status() { return status; }
    public long retryAfter() { return retryAfter; }
    public UUID invitationId() { return invitationId; }
    public String recipientHint() { return recipientHint; }
}
