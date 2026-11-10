package com.stylemind.stylist.service.session;

import com.stylemind.stylist.dto.session.CreateStylistSessionRequest;
import com.stylemind.stylist.dto.session.SessionHistoryQuery;
import com.stylemind.stylist.dto.session.StylistMessagePageResponse;
import com.stylemind.stylist.dto.session.StylistSessionResponse;
import java.util.UUID;

public interface StylistSessionService {
    StylistSessionResponse createSession(UUID customerId, CreateStylistSessionRequest request);
    StylistMessagePageResponse readMessages(UUID customerId, UUID sessionId, SessionHistoryQuery query);
}
