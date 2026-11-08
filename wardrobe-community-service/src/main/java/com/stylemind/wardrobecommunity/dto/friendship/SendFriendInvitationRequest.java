package com.stylemind.wardrobecommunity.dto.friendship;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendFriendInvitationRequest(
        @JsonProperty("recipient_email") @Email @NotBlank @Size(max = 254) String recipientEmail) {
    @Override public String toString() { return "SendFriendInvitationRequest[REDACTED]"; }
}
