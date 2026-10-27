package com.stylemind.wardrobecommunity.client.notification;

import com.stylemind.wardrobecommunity.client.notification.payload.OutfitShareInvitationRequest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Asks Notification to email an outfit share invitation. Delivery is best effort: a failure is logged as a
 * warning (without the email address) and never reaches the caller.
 */
@Component
public class ShareInvitationMailClient {

    private static final Logger log = LoggerFactory.getLogger(ShareInvitationMailClient.class);

    private final NotificationFeignClient notification;
    private final String serviceToken;
    private final String frontendBaseUrl;

    public ShareInvitationMailClient(
            NotificationFeignClient notification,
            @Value("${stylemind.security.service-token:}") String serviceToken,
            @Value("${stylemind.frontend.base-url:http://localhost:3000}") String frontendBaseUrl) {
        this.notification = notification;
        // Keep startup independent of an unset token; invitations are then skipped with a warning.
        boolean valid = serviceToken.getBytes(StandardCharsets.UTF_8).length >= 32;
        this.serviceToken = valid ? serviceToken : null;
        this.frontendBaseUrl = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1) : frontendBaseUrl;
        if (!valid) {
            log.warn("STYLEMIND_SERVICE_TOKEN is missing or shorter than 32 bytes; share invitation emails will not be sent");
        }
    }

    public void send(String recipientEmail, String sharerName, String outfitName, UUID outfitId) {
        if (serviceToken == null) {
            log.warn("Outfit share invitation skipped because no service token is configured outfitId={}", outfitId);
            return;
        }
        try {
            notification.sendOutfitShareInvitation(serviceToken, new OutfitShareInvitationRequest(recipientEmail,
                    sharerName, outfitName, frontendBaseUrl + "/shared/outfits/" + outfitId));
        } catch (RuntimeException exception) {
            log.warn("Outfit share invitation was not delivered outfitId={} cause={}", outfitId,
                    exception.getClass().getSimpleName());
        }
    }
}
