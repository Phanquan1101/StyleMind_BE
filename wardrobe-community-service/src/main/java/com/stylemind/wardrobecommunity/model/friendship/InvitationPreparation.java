package com.stylemind.wardrobecommunity.model.friendship;

import java.util.UUID;

/** Raw credential lives only between commit and the Notification call; never serialize or log this record. */
public record InvitationPreparation(InvitationActionResult result, UUID attemptId, String token, String recipientEmail) {
    public boolean sendRequired() { return attemptId != null; }
    @Override public String toString() { return "InvitationPreparation[REDACTED]"; }
}
