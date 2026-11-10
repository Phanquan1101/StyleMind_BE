package com.stylemind.stylist.service.impl.session;

import com.stylemind.stylist.dto.session.CreateStylistSessionRequest;
import com.stylemind.stylist.dto.session.SessionHistoryQuery;
import com.stylemind.stylist.dto.session.StylistMessagePageResponse;
import com.stylemind.stylist.dto.session.StylistSessionResponse;
import com.stylemind.stylist.entity.message.StylistMessageEntity;
import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import com.stylemind.stylist.mapper.session.StylistSessionMapper;
import com.stylemind.stylist.repository.message.StylistMessageRepository;
import com.stylemind.stylist.repository.session.StylistSessionRepository;
import com.stylemind.stylist.service.session.SessionHistoryCursorCodec;
import com.stylemind.stylist.service.session.SessionHistoryCursorCodec.Position;
import com.stylemind.stylist.service.session.StylistSessionService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StylistSessionServiceImpl implements StylistSessionService {

    private final StylistSessionRepository sessionRepository;
    private final StylistMessageRepository messageRepository;
    private final StylistSessionMapper mapper;
    private final SessionHistoryCursorCodec cursorCodec;
    private final Clock clock;

    public StylistSessionServiceImpl(StylistSessionRepository sessionRepository,
                                     StylistMessageRepository messageRepository,
                                     StylistSessionMapper mapper,
                                     SessionHistoryCursorCodec cursorCodec,
                                     Clock stylistClock) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.mapper = mapper;
        this.cursorCodec = cursorCodec;
        this.clock = stylistClock;
    }

    @Override
    @Transactional
    public StylistSessionResponse createSession(UUID customerId, CreateStylistSessionRequest request) {
        return mapper.response(sessionRepository.save(mapper.newSession(customerId, request, clock.instant())));
    }

    @Override
    @Transactional(readOnly = true)
    public StylistMessagePageResponse readMessages(UUID customerId, UUID sessionId, SessionHistoryQuery query) {
        sessionRepository.findByStylistSessionIdAndCustomerId(sessionId, customerId)
                .orElseThrow(() -> new StylistApiException(ErrorCode.RESOURCE_NOT_FOUND));

        List<StylistMessageEntity> found;
        if (query.before() == null) {
            found = messageRepository.findLatestForOwnerAndSession(customerId, sessionId, query.limit() + 1);
        } else {
            Position position = cursorCodec.decode(query.before(), customerId, sessionId);
            found = messageRepository.findOlderForOwnerAndSession(customerId, sessionId,
                    position.sentAt(), position.messageId(), query.limit() + 1);
        }

        boolean hasMore = found.size() > query.limit();
        List<StylistMessageEntity> visible = hasMore ? found.subList(0, query.limit()) : found;
        String nextCursor = null;
        if (hasMore) {
            StylistMessageEntity lastVisible = visible.getLast();
            nextCursor = cursorCodec.encode(customerId, sessionId,
                    lastVisible.getSentAt(), lastVisible.getStylistMessageId());
        }
        return new StylistMessagePageResponse(visible.stream().map(mapper::response).toList(), nextCursor);
    }
}
