package com.stylemind.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record FriendInvitationEmailRequest(
        @NotBlank @Email String recipientEmail,
        @NotBlank @Size(max = 100) String senderName,
        @NotBlank @Size(max = 2048) @Pattern(regexp = "https?://[^\\s\\p{Cntrl}]+") String invitationUrl,
        @NotNull OffsetDateTime expiresAt) {
    @Override public String toString() {
        return "FriendInvitationEmailRequest[recipientEmail=[REDACTED], senderName=[REDACTED], invitationUrl=[REDACTED]]";
    }
}
