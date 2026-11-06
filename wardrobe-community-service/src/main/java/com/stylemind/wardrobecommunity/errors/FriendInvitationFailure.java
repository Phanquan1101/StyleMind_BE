package com.stylemind.wardrobecommunity.errors;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class FriendInvitationFailure extends RuntimeException {
    private final HttpStatus status;
    private final long retryAfter;
    private final UUID invitationId;
    public FriendInvitationFailure(HttpStatus status, String code, long retryAfter, UUID invitationId) {
        super(code); this.status = status; this.retryAfter = retryAfter; this.invitationId = invitationId;
    }
    public HttpStatus status() { return status; }
    public long retryAfter() { return retryAfter; }
    public UUID invitationId() { return invitationId; }
}
