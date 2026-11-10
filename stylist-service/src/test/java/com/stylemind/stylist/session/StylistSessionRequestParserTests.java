package com.stylemind.stylist.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stylemind.stylist.dto.session.CreateStylistSessionRequest;
import com.stylemind.stylist.errors.StylistApiException;
import com.stylemind.stylist.service.session.StylistSessionRequestParser;
import org.junit.jupiter.api.Test;

class StylistSessionRequestParserTests {

    private final StylistSessionRequestParser parser = new StylistSessionRequestParser();

    @Test
    void rejectsAnotherJsonRootValueAfterTheSessionObject() {
        assertThatThrownBy(() -> parser.parse("{} {}".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isInstanceOf(StylistApiException.class);
    }

    @Test
    void acceptsTrailingJsonWhitespace() {
        CreateStylistSessionRequest request = parser.parse("{\"title\":null} \r\n"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(request.title()).isNull();
    }
}
