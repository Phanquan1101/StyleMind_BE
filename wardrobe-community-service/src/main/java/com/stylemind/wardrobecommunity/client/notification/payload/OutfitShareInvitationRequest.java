package com.stylemind.wardrobecommunity.client.notification.payload;

/** Private payload of Notification's internal invitation endpoint (camelCase, as that endpoint expects). */
public record OutfitShareInvitationRequest(
        String recipientEmail,
        String sharerName,
        String outfitName,
        String outfitUrl) {

    @Override
    public String toString() {
        return "OutfitShareInvitationRequest[recipientEmail=[REDACTED], sharerName=[REDACTED], "
                + "outfitName=[REDACTED], outfitUrl=[REDACTED]]";
    }
}
