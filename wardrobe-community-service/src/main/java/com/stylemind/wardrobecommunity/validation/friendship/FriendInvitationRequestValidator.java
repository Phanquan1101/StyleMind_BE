package com.stylemind.wardrobecommunity.validation.friendship;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.wardrobecommunity.dto.friendship.AcceptFriendInvitationRequest;
import com.stylemind.wardrobecommunity.dto.friendship.SendFriendInvitationRequest;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import jakarta.validation.Validator;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class FriendInvitationRequestValidator {
    private final ObjectMapper mapper;
    private final Validator validation;
    private final FriendshipRequestValidator common;
    public FriendInvitationRequestValidator(ObjectMapper mapper, Validator validation, FriendshipRequestValidator common) {
        this.mapper = mapper; this.validation = validation; this.common = common;
    }
    public String recipient(String body) {
        var object = object(body, Set.of("recipient_email"));
        var value = object.get("recipient_email");
        if (!value.isTextual()) throw invalid();
        String email = value.textValue().trim().toLowerCase(Locale.ROOT);
        if (!validation.validate(new SendFriendInvitationRequest(email)).isEmpty()) throw invalid();
        return email;
    }
    public void empty(String body) { object(body, Set.of()); }
    public AcceptFriendInvitationRequest link(String body) {
        var object = object(body, Set.of("friend_invitation_id", "token"));
        if (!object.get("friend_invitation_id").isTextual() || !object.get("token").isTextual()) throw invalid();
        String token = object.get("token").textValue();
        if (!token.matches("[A-Za-z0-9_-]{43}")) throw invalid();
        return new AcceptFriendInvitationRequest(common.friendAccountId(object.get("friend_invitation_id").textValue()), token);
    }
    private JsonNode object(String body, Set<String> fields) {
        if (body == null) throw invalid();
        try (JsonParser parser = mapper.getFactory().createParser(body)) {
            parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            JsonNode node = mapper.readTree(parser);
            if (node == null || !node.isObject() || node.size() != fields.size() || parser.nextToken() != null) throw invalid();
            for (String field : fields) if (!node.has(field)) throw invalid();
            return node;
        } catch (IOException | IllegalArgumentException failure) { throw invalid(); }
    }
    private ApiOperationException invalid() {
        return new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request is missing or malformed");
    }
}
