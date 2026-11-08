package com.stylemind.stylist.service.impl.request;

import com.stylemind.stylist.dto.request.CreateStylingRequestRequest;
import com.stylemind.stylist.dto.request.CreateStylingRequestResponse;
import com.stylemind.stylist.dto.request.StylingRequestResponse;
import com.stylemind.stylist.entity.message.StylistMessageEntity;
import com.stylemind.stylist.entity.request.StylingRequestEntity;
import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import com.stylemind.stylist.mapper.request.StylingRequestMapper;
import com.stylemind.stylist.repository.constraint.StylingConstraintRepository;
import com.stylemind.stylist.repository.message.StylistMessageRepository;
import com.stylemind.stylist.repository.request.StylingRequestRepository;
import com.stylemind.stylist.repository.session.StylistSessionRepository;
import com.stylemind.stylist.service.request.StylingRequestService;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StylingRequestServiceImpl implements StylingRequestService {

    private final StylistSessionRepository sessionRepository;
    private final StylistMessageRepository messageRepository;
    private final StylingRequestRepository requestRepository;
    private final StylingConstraintRepository constraintRepository;
    private final StylingRequestMapper mapper;
    private final Clock clock;

    public StylingRequestServiceImpl(StylistSessionRepository sessionRepository,
                                     StylistMessageRepository messageRepository,
                                     StylingRequestRepository requestRepository,
                                     StylingConstraintRepository constraintRepository,
                                     StylingRequestMapper mapper,
                                     Clock stylistClock) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.requestRepository = requestRepository;
        this.constraintRepository = constraintRepository;
        this.mapper = mapper;
        this.clock = stylistClock;
    }

    @Override
    @Transactional
    public CreateStylingRequestResponse createRequest(
            UUID customerId, UUID sessionId, CreateStylingRequestRequest request) {
        var session = sessionRepository.findOwnedForUpdate(sessionId, customerId)
                .orElseThrow(() -> new StylistApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (session.getClosedAt() != null) throw new StylistApiException(ErrorCode.SESSION_CLOSED);

        Instant now = clock.instant();
        UUID sourceMessageId = request.sourceMessageId();
        if (request.message() != null) {
            StylistMessageEntity message = mapper.newUserMessage(sessionId, request.message(), now);
            messageRepository.saveAndFlush(message);
            sourceMessageId = message.getStylistMessageId();
        } else if (sourceMessageId != null
                && !messageRepository.existsByStylistMessageIdAndStylistSessionIdAndSpeakerRole(
                        sourceMessageId, sessionId, "USER")) {
            throw new StylistApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        StylingRequestEntity stylingRequest = mapper.newRequest(sessionId, sourceMessageId, request, now);
        requestRepository.saveAndFlush(stylingRequest);
        var constraints = mapper.newConstraints(stylingRequest.getStylingRequestId(), request.constraints());
        constraintRepository.saveAll(constraints);
        return mapper.created(stylingRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public StylingRequestResponse readRequest(UUID customerId, UUID requestId) {
        StylingRequestEntity request = requestRepository.findByRequestIdAndOwner(requestId, customerId)
                .orElseThrow(() -> new StylistApiException(ErrorCode.RESOURCE_NOT_FOUND));
        return mapper.response(request,
                constraintRepository.findAllByStylingRequestIdOrderByConstraintId(requestId));
    }
}
