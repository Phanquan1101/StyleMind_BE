package com.stylemind.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.identity.dto.profile.PatchMyProfileRequest;
import com.stylemind.identity.dto.profile.ReplaceStyleProfileRequest;
import com.stylemind.identity.errors.ApiOperationException;
import com.stylemind.identity.dto.profile.StyleProfileUpdateRequest;
import com.stylemind.identity.validation.profile.ProfileRequestValidator;
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
        StyleProfileUpdateRequest emptyCommand = validator.validateReplacement(replace(empty));
        assertThat(emptyCommand.fitPreference()).isNull();
        assertThat(emptyCommand.heightCm()).isNull();
        assertThat(emptyCommand.weightKg()).isNull();
        assertThat(validator.validateReplacement(replace(empty.replace("\"weight_kg\":null,", ""))).weightKg()).isNull();
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
        StyleProfileUpdateRequest command = validator.validateReplacement(replace(valid));
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
        for (String number : List.of("100", "100.0", "230", "230.0", "172.4")) {
            assertThat(validator.validateReplacement(replace(replacement("null", number, "null", "null", "null",
                    "[]", "[]", "[]"))).heightCm()).isEqualByComparingTo(new BigDecimal(number));
        }
        for (String number : List.of("99.9", "230.1", "172.45", "150.00")) {
            assertInvalid(() -> validator.validateReplacement(replace(replacement("null", number, "null", "null",
                    "null", "[]", "[]", "[]"))));
        }
    }

    @Test
    void weightUsesExactDecimalInclusiveRangeAndAtMostOneFractionalDigit() throws Exception {
        for (String number : List.of("30", "30.0", "200", "200.0", "65.5")) {
            assertThat(validator.validateReplacement(replace(withWeight(number))).weightKg())
                    .isEqualByComparingTo(new BigDecimal(number));
        }
        for (String number : List.of("29.9", "200.1", "65.55", "70.00")) {
            assertInvalid(() -> validator.validateReplacement(replace(withWeight(number))));
        }
        assertThatThrownBy(() -> replace(withWeight("\"70\""))).isInstanceOf(JsonProcessingException.class);
    }

    private static String withWeight(String weight) {
        return replacement("null", "null", "null", "null", "null", "[]", "[]", "[]")
                .replace("\"weight_kg\":null", "\"weight_kg\":" + weight);
    }

    @Test
    void sizeLabelsAreTrimmedNormalisedAndMustBeInTheAllowedLists() throws Exception {
        assertThat(validator.validateReplacement(replace(replacement("null", "null", "\"  m  \"", "null", "null",
                "[]", "[]", "[]"))).usualTopSize()).isEqualTo("M");
        // Free-form labels, empty/blank values and control characters are no longer accepted.
        for (String bad : List.of("\"size Ñ 🧵\"", "\"" + "x".repeat(33) + "\"", "\"  \"", "\"a\\tb\"")) {
            assertInvalid(() -> validator.validateReplacement(replace(replacement("null", "null", bad, "null", "null",
                    "[]", "[]", "[]"))));
        }
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
        StyleProfileUpdateRequest brand = validator.validateReplacement(replace(replacement("null", "null", "null", "null",
                "null", "[]", "[]", "[\"" + uuid.toUpperCase() + "\"]")));
        assertThat(brand.brandIds()).containsExactly(logicalBrand);
        assertInvalid(() -> validator.validateReplacement(replace(replacement("null", "null", "null", "null", "null",
                "[]", "[]", "[\"" + uuid + "\",\"" + uuid.toUpperCase() + "\"]"))));
        assertInvalid(() -> validator.validateReplacement(replace(replacement("null", "null", "null", "null", "null",
                "[]", "[]", "[\"not-a-uuid\"]"))));
        assertInvalid(() -> validator.validateReplacement(replace(replacement("null", "null", "null", "null", "null",
                "[]", "[]", "[\"f0000000-0000-0000-0000-00000000000g\"]"))));
    }

    @Test
    void sizesMustComeFromTheAllowedListsAndErrorsNameTheField() throws Exception {
        StyleProfileUpdateRequest ok = validator.validateReplacement(replace(replacement("null", "null", "\"xxl\"", "\"30\"",
                "\"40\"", "[]", "[]", "[]")));
        assertThat(ok.usualTopSize()).isEqualTo("2XL");
        assertThat(ok.usualBottomSize()).isEqualTo("30");
        assertThat(ok.shoeSize()).isEqualTo("40");

        assertThatThrownBy(() -> validator.validateReplacement(replace(replacement("null", "99", "\"L2\"", "\"50\"",
                "\"47\"", "[]", "[]", "[]"))))
                .isInstanceOfSatisfying(com.stylemind.identity.errors.ApiOperationException.class, exception ->
                        assertThat(exception.fieldErrors()).extracting(com.stylemind.identity.errors.FieldError::field)
                                .containsExactlyInAnyOrder("height_cm", "usual_top_size", "usual_bottom_size", "shoe_size"));
    }

    private PatchMyProfileRequest patch(String json) throws JsonProcessingException {
        return mapper.readValue(json, PatchMyProfileRequest.class);
    }

    private ReplaceStyleProfileRequest replace(String json) throws JsonProcessingException {
        return mapper.readValue(json, ReplaceStyleProfileRequest.class);
    }

    private static String replacement(String fit, String height, String top, String bottom, String shoe,
            String styles, String colors, String brands) {
        return "{\"fit_preference\":" + fit + ",\"height_cm\":" + height + ",\"weight_kg\":null,\"usual_top_size\":" + top
                + ",\"usual_bottom_size\":" + bottom + ",\"shoe_size\":" + shoe
                + ",\"style_codes\":" + styles + ",\"color_codes\":" + colors + ",\"brand_ids\":" + brands + "}";
    }

    private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable validation) {
        assertThatThrownBy(validation).isInstanceOf(ApiOperationException.class)
                .extracting(exception -> ((ApiOperationException) exception).code()).isEqualTo("INVALID_REQUEST");
    }
}
