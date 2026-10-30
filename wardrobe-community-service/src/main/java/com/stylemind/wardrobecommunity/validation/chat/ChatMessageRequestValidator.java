package com.stylemind.wardrobecommunity.validation.chat;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.stylemind.wardrobecommunity.dto.chat.ChatHistoryCriteria;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.model.chat.ChatTextCommand;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ChatMessageRequestValidator {
    private static final Set<String> SEND_FIELDS = Set.of("client_message_id", "content");
    private static final Set<String> HISTORY_FIELDS = Set.of("before_sequence", "limit");
    private final ObjectReader reader;
    private final ChatSessionRequestValidator sessions;

    public ChatMessageRequestValidator(ObjectMapper mapper, ChatSessionRequestValidator sessions) {
        this.reader = mapper.readerFor(JsonNode.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.sessions = sessions;
    }

    public ChatTextCommand send(byte[] bytes) {
        if (bytes == null) { throw invalid(); }
        try {
            JsonNode node = reader.readTree(bytes);
            if (node == null || !node.isObject() || node.size() != 2) { throw invalid(); }
            var names = node.fieldNames();
            while (names.hasNext()) { if (!SEND_FIELDS.contains(names.next())) { throw invalid(); } }
            if (!node.path("client_message_id").isTextual() || !node.path("content").isTextual()) { throw invalid(); }
            var key = sessions.uuid(node.get("client_message_id").textValue());
            String text = node.get("content").textValue();
            if (text.codePointCount(0, text.length()) > 4000
                    || text.codePoints().allMatch(point -> Character.isWhitespace(point) || Character.isSpaceChar(point))) {
                throw invalid();
            }
            for (int index = 0; index < text.length(); index++) {
                char current = text.charAt(index);
                if (current == 0) { throw invalid(); }
                if (Character.isHighSurrogate(current)) {
                    if (++index == text.length() || !Character.isLowSurrogate(text.charAt(index))) { throw invalid(); }
                } else if (Character.isLowSurrogate(current)) { throw invalid(); }
            }
            return new ChatTextCommand(key, text);
        } catch (IOException exception) { throw invalid(); }
    }

    public ChatHistoryCriteria history(HttpServletRequest request) {
        var params = request.getParameterMap();
        if (!HISTORY_FIELDS.containsAll(params.keySet())) { throw invalid(); }
        params.forEach((key, values) -> {
            if (values == null || values.length != 1 || values[0] == null) { throw invalid(); }
        });
        try {
            Long before = params.containsKey("before_sequence") ? Long.parseLong(params.get("before_sequence")[0]) : null;
            int limit = params.containsKey("limit") ? Integer.parseInt(params.get("limit")[0]) : 30;
            if ((before != null && before <= 0) || limit < 1 || limit > 100) { throw invalid(); }
            return new ChatHistoryCriteria(before, limit);
        } catch (NumberFormatException exception) { throw invalid(); }
    }

    private static ApiOperationException invalid() {
        return new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request is missing or malformed");
    }
}

