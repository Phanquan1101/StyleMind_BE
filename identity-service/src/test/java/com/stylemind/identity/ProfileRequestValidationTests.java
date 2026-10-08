package com.stylemind.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.identity.dto.PatchMyProfileRequest;
import com.stylemind.identity.dto.ReplaceStyleProfileRequest;
import com.stylemind.identity.errors.ApiOperationException;
import com.stylemind.identity.model.StyleProfileCommand;
import com.stylemind.identity.validation.ProfileRequestValidator;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProfileRequestValidationTests {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ProfileRequestValidator validator = new ProfileRequestValidator();

    @Test
    void patchRequiresOnlyDisplayNameRejectsUnknownWrongTypeBlankAndControl() throws Exception {
        assertThat(validator.validatePatch(patch("{\"display_name\":\"\\u00a0  Nguyễn An  \\u3000\"}")))
                .isEqualTo("Nguyễn An");
        assertThat(validator.validatePatch(patch("{\"display_name\":\"x\"}"))).isEqualTo("x");
        assertThat(validator.validatePatch(patch("{\"display_name\":\"" + "😀".repeat(100) + "\"}")))
                .isEqualTo("😀".repeat(100));

        for (String body : List.of("{}", "{\"display_name\":null}", "{\"display_name\":\"  \"}",
                "{\"display_name\":\"\"}", "{\"display_name\":\"x\\ny\"}",
                "{\"display_name\":\"x\",\"email\":\"attacker@example.test\"}",
                "{\"display_name\":42}", "{\"display_name\":[]}")) {
            assertInvalid(() -> validator.validatePatch(patch(body)));
        }
        assertThatThrownBy(() -> patch("{" )).isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void patchCountsUnicodeCodePointsAndTrimsUnicodeWhitespace() throws Exception {
        assertThat(validator.validatePatch(patch("{\"display_name\":\"  " + "😀".repeat(100) + "  \"}")))
                .isEqualTo("😀".repeat(100));
        assertInvalid(() -> validator.validatePatch(patch("{\"display_name\":\"" + "😀".repeat(101) + "\"}")));
        assertInvalid(() -> validator.validatePatch(patch("{\"display_name\":\"a\\u0000b\"}")));
    }

    @Test
    void styleReplacementRequiresAllEightPropertiesAndAllowsNullableScalars() throws Exception {
        String empty = replacement(null, null, null, null, null, "[]", "[]", "[]");
        StyleProfileCommand emptyCommand = validator.validateReplacement(replace(empty));
        assertThat(emptyCommand.fitPreference()).isNull();
        assertThat(emptyCommand.heightCm()).isNull();
        assertThat(emptyCommand.styleCodes()).isEmpty();
        assertThat(emptyCommand.colorCodes()).isEmpty();
        assertThat(emptyCommand.brandIds()).isEmpty();

        for (String body : List.of(
                "{}",
                replacement("null", "null", "null", "null", "null", "[]", "[]", "[]")
                        .replace(",\"brand_ids\":[]", ""),
                replacement("null", "null", "null", "null", "null", "null", "[]", "[]"),
                replacement("null", "null", "null", "null", "null", "[]", "[]", "[]")
                        .replace("\"height_cm\":null,", ""),
                replacement("null", "null", "null", "null", "null", "[]", "[]", "[]")
                        .replace("\"style_codes\":[]", "\"style_codes\":null"),
                replacement("null", "null", "null", "null", "null", "[]", "[]", "[]")
                        .replace("\"shoe_size\":null", "\"shoe_size\":7"),
                replacement("null", "null", "null", "null", "null", "[]", "[]", "[]")
                        .replace("\"usual_top_size\":null", "\"usual_top_size\":[]"),
                replacement("null", "null", "null", "null", "null", "[]", "[]", "[]")
                        .replace("\"color_codes\":[]", "\"color_codes\":[null]"),
                replacement("null", "null", "null", "null", "null", "[]", "[]", "[]")
                        .replace("\"brand_ids\":[]", "\"brand_ids\":[3]"),
                replacement("null", "null", "null", "null", "null", "[]", "[]", "[]")
                        .replace("\"shoe_size\":null", "\"shoe_size\":null,\"role\":\"ADMIN\""))) {
            assertInvalid(() -> validator.validateReplacement(replace(body)));
        }
        assertThatThrownBy(() -> replace(replacement("null", "\"170\"", "null", "null", "null",
                "[]", "[]", "[]"))).isInstanceOf(JsonProcessingException.class);
        assertThatThrownBy(() -> replace("{" )).isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void codesCanonicalizeAndRejectMalformedOrCanonicalDuplicates() throws Exception {
        String valid = replacement("\" casual \"", "null", "null", "null", "null",
                "[\" street \",\"classic-2\"]", "[\"Navy\"]", "[]");
        StyleProfileCommand command = validator.validateReplacement(replace(valid));
        assertThat(command.fitPreference()).isEqualTo("CASUAL");
        assertThat(command.styleCodes()).containsExactly("STREET", "CLASSIC-2");
        assertThat(command.colorCodes()).containsExactly("NAVY");

        for (String body : List.of(
                replacement("\"bad value\"", "null", "null", "null", "null", "[]", "[]", "[]"),
                replacement("null", "null", "null", "null", "null", "[\"a\",\" A \"]", "[]", "[]"),
                replacement("null", "null", "null", "null", "null", "[]", "[\"navy\",\"NAVY\"]", "[]"),
                replacement("null", "null", "null", "null", "null", "[\"\"]", "[]", "[]"),
                replacement("null", "null", "null", "null", "null", "[\"bad value\"]", "[]", "[]"))) {
            assertInvalid(() -> validator.validateReplacement(replace(body)));
        }
        String maxCode = "A".repeat(64);
        assertThat(validator.validateReplacement(replace(replacement("null", "null", "null", "null", "null",
                "[\"" + maxCode + "\"]", "[]", "[]"))).styleCodes()).containsExactly(maxCode);
        assertInvalid(() -> validator.validateReplacement(replace(replacement("null", "null", "null", "null", "null",
                "[\"" + maxCode + "A\"]", "[]", "[]"))));
    }

    @Test
    void heightUsesExactDecimalInclusiveRangeAndAtMostOneFractionalDigit() throws Exception {
        for (String number : List.of("50", "50.0", "250", "250.0", "172.4")) {
            assertThat(validator.validateReplacement(replace(replacement("null", number, "null", "null", "null",
                    "[]", "[]", "[]"))).heightCm()).isEqualByComparingTo(new BigDecimal(number));
        }
        for (String number : List.of("49.9", "250.1", "172.45", "100.00")) {
            assertInvalid(() -> validator.validateReplacement(replace(replacement("null", number, "null", "null",
                    "null", "[]", "[]", "[]"))));
        }
    }

    @Test
    void labelsTrimPreserveFreeFormAndEnforceCodePointLimits() throws Exception {
        String body = replacement("null", "null", "\"  size Ñ 🧵  \"", "null", "null", "[]", "[]", "[]");
        assertThat(validator.validateReplacement(replace(body)).usualTopSize()).isEqualTo("size Ñ 🧵");
        assertThat(validator.validateReplacement(replace(replacement("null", "null", "\"" + "x".repeat(32)
                + "\"", "null", "null", "[]", "[]", "[]"))).usualTopSize()).hasSize(32);
        assertInvalid(() -> validator.validateReplacement(replace(replacement("null", "null", "\"" + "x".repeat(33)
                + "\"", "null", "null", "[]", "[]", "[]"))));
        assertInvalid(() -> validator.validateReplacement(replace(replacement("null", "null", "\"  \"", "null",
                "null", "[]", "[]", "[]"))));
        assertInvalid(() -> validator.validateReplacement(replace(replacement("null", "null", "\"a\\tb\"", "null",
                "null", "[]", "[]", "[]"))));
    }

    @Test
    void collectionsEnforceFiftyItemsAndBrandIdsRequireCanonicalUuidSyntaxWithoutExistenceLookup()
            throws Exception {
        String fiftyUnique = "[" + String.join(",", java.util.stream.IntStream.range(0, 50)
                .mapToObj(index -> "\"S" + index + "\"").toList()) + "]";
        assertThat(validator.validateReplacement(replace(replacement("null", "null", "null", "null", "null",
                fiftyUnique, "[]", "[]"))).styleCodes()).hasSize(50);
        String fiftyOne = "[" + String.join(",", java.util.stream.IntStream.range(0, 51)
                .mapToObj(index -> "\"S" + index + "\"").toList()) + "]";
        assertInvalid(() -> validator.validateReplacement(replace(replacement("null", "null", "null", "null", "null",
                fiftyOne, "[]", "[]"))));
        assertInvalid(() -> validator.validateReplacement(replace(replacement("null", "null", "null", "null", "null",
                "[]", fiftyOne, "[]"))));
        assertInvalid(() -> validator.validateReplacement(replace(replacement("null", "null", "null", "null", "null",
                "[]", "[]", fiftyOne))));

        String uuid = "f0000000-0000-4000-8000-000000000001";
        UUID logicalBrand = UUID.fromString(uuid);
        StyleProfileCommand brand = validator.validateReplacement(replace(replacement("null", "null", "null", "null",
                "null", "[]", "[]", "[\"" + uuid.toUpperCase() + "\"]")));
        assertThat(brand.brandIds()).containsExactly(logicalBrand);
        assertInvalid(() -> validator.validateReplacement(replace(replacement("null", "null", "null", "null", "null",
                "[]", "[]", "[\"" + uuid + "\",\"" + uuid.toUpperCase() + "\"]"))));
        assertInvalid(() -> validator.validateReplacement(replace(replacement("null", "null", "null", "null", "null",
                "[]", "[]", "[\"not-a-uuid\"]"))));
        assertInvalid(() -> validator.validateReplacement(replace(replacement("null", "null", "null", "null", "null",
                "[]", "[]", "[\"f0000000-0000-0000-0000-00000000000g\"]"))));
    }

    private PatchMyProfileRequest patch(String json) throws JsonProcessingException {
        return mapper.readValue(json, PatchMyProfileRequest.class);
    }

    private ReplaceStyleProfileRequest replace(String json) throws JsonProcessingException {
        return mapper.readValue(json, ReplaceStyleProfileRequest.class);
    }

    private static String replacement(String fit, String height, String top, String bottom, String shoe,
            String styles, String colors, String brands) {
        return "{\"fit_preference\":" + fit + ",\"height_cm\":" + height + ",\"usual_top_size\":" + top
                + ",\"usual_bottom_size\":" + bottom + ",\"shoe_size\":" + shoe
                + ",\"style_codes\":" + styles + ",\"color_codes\":" + colors + ",\"brand_ids\":" + brands + "}";
    }

    private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable validation) {
        assertThatThrownBy(validation).isInstanceOf(ApiOperationException.class)
                .extracting(exception -> ((ApiOperationException) exception).code()).isEqualTo("INVALID_REQUEST");
    }
}
