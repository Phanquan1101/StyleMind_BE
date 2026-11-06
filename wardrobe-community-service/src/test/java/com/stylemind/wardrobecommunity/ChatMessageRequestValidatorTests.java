package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.validation.chat.ChatMessageRequestValidator;
import com.stylemind.wardrobecommunity.validation.chat.ChatSessionRequestValidator;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ChatMessageRequestValidatorTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ChatMessageRequestValidator validator = new ChatMessageRequestValidator(mapper, new ChatSessionRequestValidator(mapper));
    private static final String KEY = "40000000-0000-0000-0000-000000000004";

    @Test void preservesWhitespaceVietnameseAndSupplementaryUnicodeAt4000CodePoints() throws Exception {
        String text = "ế".repeat(2000) + "😀".repeat(2000);
        assertThat(validator.send(payload(text)).content()).isEqualTo(text);
        assertThat(validator.send(payload("  chào\n\t ")).content()).isEqualTo("  chào\n\t ");
        invalid(() -> validator.send(payloadUnchecked(text + "x")));
    }
    @Test void rejectsEmptyWhitespaceMissingWrongTypesAndUnsupportedImages() throws Exception {
        for (String text : new String[] {"", " ", "\n\t", "\u00a0\u2003"}) { invalid(() -> validator.send(payloadUnchecked(text))); }
        for (String raw : new String[] {"{}", "null", "[]", "{\"client_message_id\":\"" + KEY + "\",\"content\":null}",
                "{\"client_message_id\":\"" + KEY + "\",\"content\":5}",
                "{\"client_message_id\":\"1-1-1-1-1\",\"content\":\"hi\"}",
                "{\"client_message_id\":\"" + KEY + "\",\"content\":\"hi\",\"sender_account_id\":\"" + KEY + "\"}",
                "{\"client_message_id\":\"" + KEY + "\",\"content\":\"hi\",\"images\":[]}"}) {
            invalid(() -> validator.send(raw.getBytes(StandardCharsets.UTF_8)));
        }
        invalid(() -> validator.send(null));
    }
    @Test void rejectsDuplicateKeysTrailingJsonAndMalformedBytes() {
        String raw = "{\"client_message_id\":\"" + KEY + "\",\"content\":\"a\",\"content\":\"b\"}";
        invalid(() -> validator.send(raw.getBytes(StandardCharsets.UTF_8)));
        invalid(() -> validator.send((new String(payloadUnchecked("ok"), StandardCharsets.UTF_8) + "{}").getBytes(StandardCharsets.UTF_8)));
        invalid(() -> validator.send(new byte[] {(byte) 0xc3, (byte) 0x28}));
    }
    @Test void rejectsNulAndLoneSurrogatesBeforePostgresWhileAllowingNormalNewlines() {
        invalid(() -> validator.send(payloadUnchecked("a\u0000b")));
        invalid(() -> validator.send(("{\"client_message_id\":\"" + KEY + "\",\"content\":\"\\ud800\"}").getBytes(StandardCharsets.UTF_8)));
        invalid(() -> validator.send(("{\"client_message_id\":\"" + KEY + "\",\"content\":\"\\udc00\"}").getBytes(StandardCharsets.UTF_8)));
    }
    @Test void cursorDefaultsAndBoundariesDoNotLoseLongPrecision() {
        var defaults = validator.history(new MockHttpServletRequest());
        assertThat(defaults.limit()).isEqualTo(30);
        assertThat(defaults.beforeSequence()).isNull();
        var request = new MockHttpServletRequest();
        request.addParameter("before_sequence", Long.toString(Long.MAX_VALUE));
        request.addParameter("limit", "100");
        assertThat(validator.history(request).beforeSequence()).isEqualTo(Long.MAX_VALUE);
    }
    @Test void rejectsInvalidCursorLimitDuplicatesAndOwnerOverride() {
        for (String[] entry : new String[][] {{"before_sequence","0"},{"before_sequence","-1"},{"before_sequence","9223372036854775808"},
                {"before_sequence","1.5"},{"limit","0"},{"limit","101"},{"limit","x"},{"sender_account_id",KEY}}) {
            var request = new MockHttpServletRequest(); request.addParameter(entry[0],entry[1]);
            invalid(() -> validator.history(request));
        }
        var duplicate = new MockHttpServletRequest(); duplicate.addParameter("limit","1","2");
        invalid(() -> validator.history(duplicate));
    }
    private byte[] payload(String text) throws Exception { return mapper.writeValueAsBytes(Map.of("client_message_id",KEY,"content",text)); }
    private byte[] payloadUnchecked(String text) {
        try { return payload(text); } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private void invalid(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiOperationException.class,
                ex -> assertThat(ex.code()).isEqualTo("INVALID_REQUEST"));
    }
}

