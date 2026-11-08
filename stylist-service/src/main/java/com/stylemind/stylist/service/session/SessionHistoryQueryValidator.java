package com.stylemind.stylist.service.session;

import com.stylemind.stylist.dto.session.SessionHistoryQuery;
import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SessionHistoryQueryValidator {

    private static final Set<String> ALLOWED_KEYS = Set.of("limit", "before");

    public UUID sessionId(String rawSessionId) {
        try {
            UUID id = UUID.fromString(rawSessionId);
            if (!id.toString().equalsIgnoreCase(rawSessionId)) throw invalidRequest();
            return id;
        } catch (IllegalArgumentException exception) {
            throw invalidRequest();
        }
    }

    public SessionHistoryQuery parse(HttpServletRequest request) {
        Map<String, String[]> parameters = request.getParameterMap();
        if (!ALLOWED_KEYS.containsAll(parameters.keySet())) throw invalidRequest();
        for (String[] values : parameters.values()) {
            if (values == null || values.length != 1) throw invalidRequest();
        }

        int limit = 20;
        String rawLimit = request.getParameter("limit");
        if (rawLimit != null) {
            if (!rawLimit.matches("[0-9]{1,3}")) throw invalidRequest();
            try {
                limit = Integer.parseInt(rawLimit);
            } catch (NumberFormatException exception) {
                throw invalidRequest();
            }
            if (limit < 1 || limit > 50) throw invalidRequest();
        }

        String before = request.getParameter("before");
        if (before != null && (before.isBlank() || before.length() > 128)) throw invalidRequest();
        return new SessionHistoryQuery(limit, before);
    }

    private static StylistApiException invalidRequest() {
        return new StylistApiException(ErrorCode.INVALID_REQUEST);
    }
}
