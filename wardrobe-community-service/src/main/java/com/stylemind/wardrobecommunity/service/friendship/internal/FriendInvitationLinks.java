package com.stylemind.wardrobecommunity.service.friendship.internal;

import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import java.net.URI;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class FriendInvitationLinks {
    private final String page;
    public FriendInvitationLinks(@Value("${stylemind.friends.invitation-page-url:}") String page) {
        String validated = null;
        try {
            URI uri = URI.create(page);
            String ascii = uri.toASCIIString();
            if (("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) && uri.getHost() != null
                    && uri.getRawUserInfo() == null && uri.getRawFragment() == null && ascii.length() <= 1800) {
                validated = ascii;
            }
        } catch (IllegalArgumentException ignored) { /* Invalid local configuration is never exposed. */ }
        this.page = validated;
    }
    public void requireConfigured() {
        if (page == null) throw new ApiOperationException(HttpStatus.SERVICE_UNAVAILABLE,
                "FRIEND_INVITATION_UNAVAILABLE", "Invitation page is not configured");
    }
    public String link(UUID id, String token) {
        requireConfigured();
        return page + "#invitation_id=" + id + "&token=" + token;
    }
}
