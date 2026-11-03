package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.validation.chat.ChatSessionRequestValidator;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ChatSessionRequestValidatorTests {
    private static final String PEER = "80000000-0000-0000-0000-0000000000ab";
    private final ObjectMapper mapper = new ObjectMapper();
    private final ChatSessionRequestValidator validator = new ChatSessionRequestValidator(mapper);

    @Test
    void openRequiresOneFullUuidPeerAndAcceptsUppercaseHex() {
        assertThat(validator.open("{\"friend_account_id\":\"" + PEER.toUpperCase() + "\"}"))
                .isEqualTo(UUID.fromString(PEER));
        assertThat(validator.uuid(PEER.toUpperCase())).isEqualTo(UUID.fromString(PEER));
    }

    @Test
    void rejectsMissingWrongTypedUnknownAndSelfDeclaredActorFields() {
        for (String body : new String[] {null, "", "null", "[]", "{}", "{\"friend_account_id\":null}",
                "{\"friend_account_id\":12}", "{\"friend_account_id\":[]}",
                "{\"friend_account_id\":\"\"}", "{\"friend_account_id\":\"1-1-1-1-1\"}",
                "{\"friend_account_id\":\"" + PEER + "\",\"actor_id\":\"" + PEER + "\"}",
                "{\"friend_account_id\":\"" + PEER + "\",\"owner\":null}"}) {
            invalid(() -> validator.open(body));
        }
    }

    @Test
    void rejectsDuplicateKeysTrailingDocumentsAndMalformedJsonLocally() {
        for (String body : new String[] {
                "{\"friend_account_id\":\"" + PEER + "\",\"friend_account_id\":\"" + PEER + "\"}",
                "{\"friend_account_id\":\"" + PEER + "\"}{}",
                "{\"friend_account_id\":\"" + PEER + "\"} true", "{invalid}"}) {
            invalid(() -> validator.open(body));
        }
        // Configuring the Chat reader must not change the application's shared ObjectMapper.
        assertThat(mapper.getFactory().isEnabled(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)).isFalse();
    }

    @Test
    void uuidRejectsAbbreviationsWhitespaceAndMalformedValues() {
        for (String value : new String[] {null, "", "1-1-1-1-1", " " + PEER, PEER + " ", PEER.replace("-", ""), "not-a-uuid"}) {
            invalid(() -> validator.uuid(value));
        }
    }

    @Test
    void listUsesDefaultsAndAcceptsBoundsWithoutIntegerOverflow() {
        var defaults = validator.list(new MockHttpServletRequest());
        assertThat(defaults.page()).isZero();
        assertThat(defaults.size()).isEqualTo(20);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("page", "2147483647");
        request.addParameter("size", "100");
        assertThat(validator.list(request).page()).isEqualTo(Integer.MAX_VALUE);
        assertThat(validator.list(request).size()).isEqualTo(100);
    }

    @Test
    void listRejectsUnknownDuplicateNonNumericAndOutOfRangeParams() {
        for (String[] entry : new String[][] {{"page", "-1"}, {"page", "2147483648"}, {"page", "1.5"},
                {"page", " "}, {"size", "0"}, {"size", "101"}, {"size", "abc"}, {"actor_id", PEER}}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addParameter(entry[0], entry[1]);
            invalid(() -> validator.list(request));
        }
        MockHttpServletRequest duplicate = new MockHttpServletRequest();
        duplicate.addParameter("page", "0", "1");
        invalid(() -> validator.list(duplicate));
    }

    @Test
    void nonListRoutesRejectAnyQueryIncludingOwnerOverride() {
        validator.noQuery(new MockHttpServletRequest());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("owner", PEER);
        invalid(() -> validator.noQuery(request));
    }

    private void invalid(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiOperationException.class,
                exception -> {
                    assertThat(exception.status().value()).isEqualTo(400);
                    assertThat(exception.code()).isEqualTo("INVALID_REQUEST");
                });
    }
}

