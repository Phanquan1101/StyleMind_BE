package com.stylemind.wardrobecommunity.validation.chat;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.stylemind.wardrobecommunity.dto.chat.ChatPageCriteria;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ChatSessionRequestValidator {
    private static final Pattern UUID_TEXT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Set<String> LIST_FIELDS = Set.of("page", "size");
    private final ObjectReader reader;

    public ChatSessionRequestValidator(ObjectMapper mapper) {
        reader = mapper.readerFor(JsonNode.class)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    public UUID open(String body) {
        if (body == null) {
            throw invalid();
        }
        try {
            JsonNode node = reader.readTree(body);
            if (node == null || !node.isObject() || node.size() != 1
                    || !node.has("friend_account_id") || !node.get("friend_account_id").isTextual()) {
                throw invalid();
            }
            return uuid(node.get("friend_account_id").textValue());
        } catch (JsonProcessingException exception) {
            throw invalid();
        }
    }

    public UUID uuid(String value) {
        if (value == null || !UUID_TEXT.matcher(value).matches()) {
            throw invalid();
        }
        return UUID.fromString(value);
    }

    public ChatPageCriteria list(HttpServletRequest request) {
        Map<String, String[]> params = request.getParameterMap();
        if (!LIST_FIELDS.containsAll(params.keySet())) {
            throw invalid();
        }
        params.forEach((key, values) -> {
            if (values == null || values.length != 1 || values[0] == null) {
                throw invalid();
            }
        });
        int page = number(params.get("page"), 0);
        int size = number(params.get("size"), 20);
        if (page < 0 || size < 1 || size > 100) {
            throw invalid();
        }
        return new ChatPageCriteria(page, size);
    }

    public void noQuery(HttpServletRequest request) {
        if (!request.getParameterMap().isEmpty()) {
            throw invalid();
        }
    }

    private int number(String[] values, int fallback) {
        if (values == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(values[0]);
        } catch (NumberFormatException exception) {
            throw invalid();
        }
    }

    private static ApiOperationException invalid() {
        return new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request is missing or malformed");
    }
}

