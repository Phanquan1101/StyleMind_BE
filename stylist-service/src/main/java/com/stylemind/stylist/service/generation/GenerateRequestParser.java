package com.stylemind.stylist.service.generation;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.stylemind.stylist.dto.generation.GenerateRequest;
import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import java.io.IOException;
import org.springframework.stereotype.Component;

@Component
public class GenerateRequestParser {
    private final com.fasterxml.jackson.databind.ObjectMapper strictMapper = JsonMapper.builder(
                    JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    public GenerateRequest parse(byte[] body) {
        try {
            JsonNode root = strictMapper.readTree(body);
            if (root == null || !root.isObject() || root.size() != 0) {
                throw new StylistApiException(ErrorCode.INVALID_REQUEST);
            }
            return new GenerateRequest();
        } catch (IOException exception) {
            throw new StylistApiException(ErrorCode.INVALID_REQUEST);
        }
    }
}
