package com.stylemind.wardrobecommunity.validation.friendship;

import com.stylemind.wardrobecommunity.dto.friendship.FriendPageCriteria;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class FriendshipRequestValidator {

    private static final Set<String> LIST_FIELDS = Set.of("page", "size");
    private static final Pattern UUID_TEXT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    public FriendPageCriteria list(HttpServletRequest request) {
        Map<String, String[]> params = request.getParameterMap();
        if (!LIST_FIELDS.containsAll(params.keySet())) {
            throw invalidRequest();
        }
        params.forEach((name, values) -> {
            if (values == null || values.length != 1 || values[0] == null) {
                throw invalidRequest();
            }
        });
        int page = number(params.get("page"), 0);
        int size = number(params.get("size"), 20);
        if (page < 0 || size < 1 || size > 100) {
            throw invalidRequest();
        }
        return new FriendPageCriteria(page, size);
    }

    public UUID friendAccountId(String value) {
        if (value == null || !UUID_TEXT.matcher(value).matches()) {
            throw invalidRequest();
        }
        return UUID.fromString(value);
    }

    public void noQuery(HttpServletRequest request) {
        if (!request.getParameterMap().isEmpty()) {
            throw invalidRequest();
        }
    }

    private int number(String[] raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw[0]);
        } catch (NumberFormatException exception) {
            throw invalidRequest();
        }
    }

    private ApiOperationException invalidRequest() {
        return new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request is missing or malformed");
    }
}
