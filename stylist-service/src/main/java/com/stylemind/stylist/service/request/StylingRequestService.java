package com.stylemind.stylist.service.request;

import com.stylemind.stylist.dto.request.CreateStylingRequestRequest;
import com.stylemind.stylist.dto.request.CreateStylingRequestResponse;
import com.stylemind.stylist.dto.request.StylingRequestResponse;
import java.util.UUID;

public interface StylingRequestService {
    CreateStylingRequestResponse createRequest(UUID customerId, UUID sessionId, CreateStylingRequestRequest request);
    StylingRequestResponse readRequest(UUID customerId, UUID requestId);
}
