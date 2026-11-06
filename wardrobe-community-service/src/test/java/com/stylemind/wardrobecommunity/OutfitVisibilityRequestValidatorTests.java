package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.validation.outfit.OutfitVisibilityRequestValidator;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OutfitVisibilityRequestValidatorTests {
    private final OutfitVisibilityRequestValidator validator =
            new OutfitVisibilityRequestValidator(new ObjectMapper());

    @ParameterizedTest
    @ValueSource(strings = {"PRIVATE", "FRIENDS", "PUBLIC"})
    void acceptsOnlyTheThreeExplicitModes(String mode) {
        assertThat(validator.visibility("{\"visibility\":\"" + mode + "\"}")).isEqualTo(mode);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                " ",
                "null",
                "[]",
                "{}",
                "true",
                "{",
                "{\"visibility\":null}",
                "{\"visibility\":3}",
                "{\"visibility\":\"private\"}",
                "{\"visibility\":\" FRIENDS\"}",
                "{\"visibility\":\"PRIVATE\",\"owner_id\":\"other\"}",
                "{\"visibility\":\"PRIVATE\",\"visibility\":\"PUBLIC\"}",
                "{\"visibility\":\"PRIVATE\"} {}",
                "{\"visibility\":[\"PUBLIC\"]}",
                "{\"visibility\":\"PUBLIC\",\"public_token\":\"chosen\"}"
            })
    void rejectsMalformedAmbiguousAndClientControlledState(String body) {
        assertThatThrownBy(() -> validator.visibility(body))
                .isInstanceOfSatisfying(
                        ApiOperationException.class,
                        e -> {
                            assertThat(e.status().value()).isEqualTo(400);
                            assertThat(e.code()).isEqualTo("INVALID_REQUEST");
                        });
    }
}
