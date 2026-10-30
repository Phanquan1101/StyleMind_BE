package com.stylemind.wardrobecommunity.websocket;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class ChatSocketSessions {
    public record Identity(UUID accountId, Instant expiresAt) { }
    private final ConcurrentHashMap<String, Identity> identities = new ConcurrentHashMap<>();
    private final Clock clock;

    public ChatSocketSessions(@Qualifier("chatSocketClock") Clock clock) { this.clock = clock; }

    public boolean register(String sessionId, UUID accountId, Instant expiresAt) {
        return sessionId != null && expiresAt != null && clock.instant().isBefore(expiresAt)
                && identities.putIfAbsent(sessionId, new Identity(accountId, expiresAt)) == null;
    }

    public Identity valid(String sessionId) {
        if (sessionId == null) { return null; }
        Identity identity = identities.get(sessionId);
        return identity != null && clock.instant().isBefore(identity.expiresAt()) ? identity : null;
    }

    public void remove(String sessionId) { if (sessionId != null) identities.remove(sessionId); }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) { remove(event.getSessionId()); }
}

