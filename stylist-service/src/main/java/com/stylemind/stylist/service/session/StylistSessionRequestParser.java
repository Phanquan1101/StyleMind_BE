package com.stylemind.stylist.service.session;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.stylemind.stylist.dto.session.CreateStylistSessionRequest;
import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import java.io.IOException;
import java.util.Iterator;
import org.springframework.stereotype.Component;

@Component
public class StylistSessionRequestParser {

    private final ObjectMapper strictMapper;

    public StylistSessionRequestParser() {
        JsonFactory factory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
        this.strictMapper = JsonMapper.builder(factory)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
    }

    public CreateStylistSessionRequest parse(byte[] json) {
        try {
            JsonNode root = strictMapper.readTree(json);
            if (root == null || !root.isObject()) throw invalidRequest();
            Iterator<String> names = root.fieldNames();
            while (names.hasNext()) {
                if (!"title".equals(names.next())) throw invalidRequest();
            }
            JsonNode titleNode = root.get("title");
            if (titleNode == null || titleNode.isNull()) return new CreateStylistSessionRequest(null);
            if (!titleNode.isTextual()) throw invalidRequest();

            String title = titleNode.textValue().strip();
            int codePoints = title.codePointCount(0, title.length());
            if (codePoints < 1 || codePoints > 100 || title.codePoints().anyMatch(Character::isISOControl))
                throw invalidRequest();
            return new CreateStylistSessionRequest(title);
        } catch (IOException exception) {
            throw invalidRequest();
        }
    }

    private static StylistApiException invalidRequest() {
        return new StylistApiException(ErrorCode.INVALID_REQUEST);
    }
}
