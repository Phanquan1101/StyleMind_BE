package com.stylemind.stylist.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import com.stylemind.stylist.service.request.StylingRequestParser;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class StylingRequestParserUnicodeTests {

    private final StylingRequestParser parser = new StylingRequestParser();

    @Test
    void rejectsValuesContainingOnlyUnicodeSpaceCharacters() {
        assertInvalidRequest("{\"message\":\"\\u00a0\"}");
        assertInvalidRequest("{\"occasion\":\"\\u202f\"}");
        assertInvalidRequest("{\"general_weather_context\":\"\\u00a0\\u202f\"}");
        assertInvalidRequest("{\"occasion\":\"work\",\"constraints\":["
                + "{\"constraint_kind\":\"EXCLUDE_COLOR\",\"text_value\":\"\\u00a0\",\"is_hard\":true}]}");
    }

    @Test
    void trimsUnicodeSpacesAroundRealTextAndPreservesWeatherNewlines() {
        var parsed = parser.parse(("{\"message\":\"\\u00a0  need an outfit  \\u202f\","
                + "\"occasion\":\"\\u202fwork\\u00a0\","
                + "\"general_weather_context\":\"\\u00a0warm\\nlight rain\\u202f\","
                + "\"constraints\":[{\"constraint_kind\":\"EXCLUDE_COLOR\","
                + "\"text_value\":\"\\u00a0RED\\u202f\",\"is_hard\":true}]}")
                .getBytes(StandardCharsets.UTF_8));

        assertThat(parsed.message()).isEqualTo("need an outfit");
        assertThat(parsed.occasion()).isEqualTo("work");
        assertThat(parsed.generalWeatherContext()).isEqualTo("warm\nlight rain");
        assertThat(parsed.constraints().getFirst().textValue()).isEqualTo("RED");
    }

    @Test
    void rejectsNulButKeepsMultilineWeatherText() {
        assertInvalidRequest("{\"occasion\":\"work\",\"general_weather_context\":\"hot\\u0000today\"}");
        var parsed = parser.parse("{\"general_weather_context\":\"warm\\nlight rain\"}"
                .getBytes(StandardCharsets.UTF_8));
        assertThat(parsed.generalWeatherContext()).isEqualTo("warm\nlight rain");
    }

    @Test
    void keepsExistingCodePointLimitsIncludingSupplementaryCharacters() {
        assertAccepts("{\"message\":\"" + "😀".repeat(2_000) + "\"}");
        assertInvalidRequest("{\"message\":\"" + "😀".repeat(2_001) + "\"}");

        for (String field : new String[] {"occasion", "intended_use", "style_context"}) {
            assertAccepts("{\"" + field + "\":\"" + "😀".repeat(128) + "\"}");
            assertInvalidRequest("{\"" + field + "\":\"" + "😀".repeat(129) + "\"}");
        }

        assertAccepts("{\"general_weather_context\":\"" + "😀".repeat(1_000) + "\"}");
        assertInvalidRequest("{\"general_weather_context\":\"" + "😀".repeat(1_001) + "\"}");

        String constraintPrefix = "{\"occasion\":\"work\",\"constraints\":[{"
                + "\"constraint_kind\":\"EXCLUDE_COLOR\",\"text_value\":\"";
        String constraintSuffix = "\",\"is_hard\":true}]}";
        assertAccepts(constraintPrefix + "😀".repeat(64) + constraintSuffix);
        assertInvalidRequest(constraintPrefix + "😀".repeat(65) + constraintSuffix);
    }

    private void assertAccepts(String json) {
        parser.parse(json.getBytes(StandardCharsets.UTF_8));
    }

    private void assertInvalidRequest(String json) {
        assertThatThrownBy(() -> parser.parse(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(StylistApiException.class)
                .satisfies(exception -> assertThat(((StylistApiException) exception).errorCode())
                        .isEqualTo(ErrorCode.INVALID_REQUEST));
    }
}
