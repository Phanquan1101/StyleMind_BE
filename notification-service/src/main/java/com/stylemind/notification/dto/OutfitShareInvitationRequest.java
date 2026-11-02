package com.stylemind.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OutfitShareInvitationRequest(
        @NotBlank @Email String recipientEmail,
        @NotBlank @Size(max = 100) String sharerName,
        @NotBlank @Size(max = 100) String outfitName,
        @NotBlank @Size(max = 2048) @Pattern(regexp = "https?://[^\\s\\p{Cntrl}]+") String outfitUrl) {

    @Override
    public String toString() {
        return "OutfitShareInvitationRequest[recipientEmail=[REDACTED], sharerName=[REDACTED], "
                + "outfitName=[REDACTED], outfitUrl=[REDACTED]]";
    }
}
