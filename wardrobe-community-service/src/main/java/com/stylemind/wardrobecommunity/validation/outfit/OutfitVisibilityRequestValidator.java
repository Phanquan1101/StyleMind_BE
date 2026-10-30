package com.stylemind.wardrobecommunity.validation.outfit;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

@Component
public class OutfitVisibilityRequestValidator {
    private final ObjectMapper mapper;

    public OutfitVisibilityRequestValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String visibility(String body) {
        if (body == null) throw invalid();
        try (JsonParser parser = mapper.getFactory().createParser(body)) {
            parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(parser);
            if (node == null
                    || !node.isObject()
                    || node.size() != 1
                    || !node.has("visibility")
                    || !node.get("visibility").isTextual()
                    || parser.nextToken() != null
                    || !Set.of("PRIVATE", "FRIENDS", "PUBLIC")
                            .contains(node.get("visibility").textValue())) throw invalid();
            return node.get("visibility").textValue();
        } catch (IOException | IllegalArgumentException invalid) {
            throw invalid();
        }
    }

    private ApiOperationException invalid() {
        return new ApiOperationException(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "Visibility must be PRIVATE, FRIENDS or PUBLIC");
    }
}
