package com.stylemind.identity.validation.internal;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.identity.errors.ApiOperationException;
import jakarta.validation.Validator;
import java.io.IOException;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class EmailLookupRequestValidator {
    private final ObjectMapper mapper;
    private final Validator validator;
    public EmailLookupRequestValidator(ObjectMapper mapper, Validator validator) {
        this.mapper = mapper; this.validator = validator;
    }
    public String email(String body) {
        try (JsonParser parser = mapper.getFactory().createParser(body)) {
            parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(parser);
            if (node == null || !node.isObject() || node.size() != 1 || !node.has("email")
                    || !node.get("email").isTextual() || parser.nextToken() != null) throw invalid();
            String email = node.get("email").textValue().trim().toLowerCase(Locale.ROOT);
            if (!validator.validate(new com.stylemind.identity.dto.internal.AccountEmailLookupRequest(email)).isEmpty()) throw invalid();
            return email;
        } catch (IOException | IllegalArgumentException failure) { throw invalid(); }
    }
    private ApiOperationException invalid() {
        return new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request is missing or malformed", 0);
    }
}
