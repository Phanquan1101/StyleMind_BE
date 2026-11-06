package com.stylemind.wardrobecommunity.client.notification.payload;

import java.time.OffsetDateTime;

public record FriendInvitationEmailRequest(
        String recipientEmail, String senderName, String invitationUrl, OffsetDateTime expiresAt) {
    @Override public String toString() {
        return "FriendInvitationEmailRequest[recipientEmail=[REDACTED], senderName=[REDACTED], invitationUrl=[REDACTED]]";
    }
}
