package com.stylemind.stylist.mapper.session;

import com.stylemind.stylist.dto.session.CreateStylistSessionRequest;
import com.stylemind.stylist.dto.session.StylistMessageResponse;
import com.stylemind.stylist.dto.session.StylistSessionResponse;
import com.stylemind.stylist.entity.message.StylistMessageEntity;
import com.stylemind.stylist.entity.session.StylistSessionEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class StylistSessionMapper {

    public StylistSessionEntity newSession(UUID customerId, CreateStylistSessionRequest request, Instant openedAt) {
        return new StylistSessionEntity(UUID.randomUUID(), customerId, request.title(), openedAt, null);
    }

    public StylistSessionResponse response(StylistSessionEntity session) {
        return new StylistSessionResponse(session.getStylistSessionId(), session.getTitle(),
                session.getOpenedAt(), session.getClosedAt());
    }

    public StylistMessageResponse response(StylistMessageEntity message) {
        return new StylistMessageResponse(message.getStylistMessageId(), message.getSpeakerRole(),
                message.getContent(), message.getSentAt());
    }
}
