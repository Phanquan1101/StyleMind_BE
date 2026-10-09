package com.stylemind.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.identity.directory.VietnamAdministrativeDirectory;
import com.stylemind.identity.errors.ApiOperationException;
import com.stylemind.identity.validation.AddressRequestValidator;
import com.stylemind.identity.validation.VietnamPhoneValidator;
import org.junit.jupiter.api.Test;

class AddressRequestValidationTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final VietnamPhoneValidator phone = new VietnamPhoneValidator();
    private final AddressRequestValidator validator = new AddressRequestValidator(phone,
            new VietnamAdministrativeDirectory(mapper));

    @Test
    void phoneAcceptsDomesticAndInternationalVietnamFormsAndCanonicalizesToE164() {
        assertThat(phone.canonicalize(" 0912 345 678 ")).isEqualTo("+84912345678");
        assertThat(phone.canonicalize("+84 (912) 345-678")).isEqualTo("+84912345678");
        assertThat(phone.canonicalize("024 3939 3939")).startsWith("+8424");
        for (String invalid : new String[] {"+1 202 555 0142", "0123", "0912CALL678", "0912345678 ext 1",
                "0912#678", "+849+12345678", "0912\n345678", ""}) {
            assertInvalid(() -> phone.canonicalize(invalid));
        }
        assertThat(phone.canonicalize(" ".repeat(54) + "0912345678")).isEqualTo("+84912345678");
        assertInvalid(() -> phone.canonicalize(" ".repeat(55) + "0912345678"));
    }

    @Test
    void createTrimsUnicodeAndRejectsTypesUnknownFieldsWardAndSensitiveInjection() throws Exception {
        var created = validator.validateCreate(node("{\"label\":\"  Nhà  \",\"recipient_name\":\"  Nguyễn An  \","
                + "\"recipient_phone\":\"0912345678\",\"address_line\":\"  12 Đường A  \",\"ward_code\":\" 00004 \"}"));
        assertThat(created.label()).isEqualTo("Nhà");
        assertThat(created.recipientName()).isEqualTo("Nguyễn An");
        assertThat(created.addressLine()).isEqualTo("12 Đường A");
        assertThat(created.recipientPhone()).isEqualTo("+84912345678");
        assertThat(created.wardCode()).isEqualTo("00004");
        for (String invalid : new String[] {"{}", "null", "[]", "{\"recipient_name\":7}",
                "{\"recipient_name\":\"A\",\"recipient_phone\":true}",
                "{\"recipient_name\":\"A\",\"recipient_phone\":\"0912345678\",\"address_line\":\"X\",\"is_default\":true}",
                "{\"recipient_name\":\"A\",\"recipient_phone\":\"0912345678\",\"address_line\":\"X\",\"account_id\":\"id\"}",
                "{\"recipient_name\":\"A\",\"recipient_phone\":\"0912345678\",\"address_line\":\"X\",\"ward_code\":123}",
                "{\"recipient_name\":\"A\",\"recipient_phone\":\"0912345678\",\"address_line\":\"X\",\"ward_code\":\"999999\"}"}) {
            assertInvalid(() -> validator.validateCreate(node(invalid)));
        }
    }

    @Test
    void patchDistinguishesOmittedFromNullAndEnforcesUnicodeCodePointLimits() throws Exception {
        var keep = validator.validatePatch(node("{\"recipient_name\":\"  An  \"}"));
        assertThat(keep.recipientNamePresent()).isTrue();
        assertThat(keep.labelPresent()).isFalse();
        assertThat(keep.wardCodePresent()).isFalse();
        var clear = validator.validatePatch(node("{\"label\":null,\"ward_code\":null}"));
        assertThat(clear.labelPresent()).isTrue();
        assertThat(clear.label()).isNull();
        assertThat(clear.wardCodePresent()).isTrue();
        for (String invalid : new String[] {"{}", "{\"recipient_name\":null}", "{\"address_line\":42}",
                "{\"label\":\" \"}", "{\"label\":\"" + "😀".repeat(51) + "\"}",
                "{\"recipient_name\":\"" + "😀".repeat(101) + "\"}",
                "{\"address_line\":\"a\\nb\"}"}) {
            assertInvalid(() -> validator.validatePatch(node(invalid)));
        }
        assertThat(validator.validatePatch(node("{\"recipient_name\":\"" + "😀".repeat(100) + "\"}"))
                .recipientName()).hasSize(200);
    }

    @Test
    void textBoundariesAreInclusiveAndOptionalCreateFieldsDefaultToNull() throws Exception {
        String maxLabel = "L".repeat(50);
        String maxName = "N".repeat(100);
        String maxLine = "A".repeat(500);
        var values = validator.validateCreate(node("{\"recipient_name\":\"" + maxName
                + "\",\"recipient_phone\":\"0912345678\",\"address_line\":\"" + maxLine
                + "\",\"label\":\"" + maxLabel + "\"}"));
        assertThat(values.label()).hasSize(50);
        assertThat(values.recipientName()).hasSize(100);
        assertThat(values.addressLine()).hasSize(500);
        var optional = validator.validateCreate(node("{\"recipient_name\":\"A\",\"recipient_phone\":\"0912345678\","
                + "\"address_line\":\"A\"}"));
        assertThat(optional.label()).isNull();
        assertThat(optional.wardCode()).isNull();
        assertInvalid(() -> validator.validateCreate(node("{\"label\":\"" + "L".repeat(51)
                + "\",\"recipient_name\":\"A\",\"recipient_phone\":\"0912345678\",\"address_line\":\"A\"}")));
        assertInvalid(() -> validator.validateCreate(node("{\"recipient_name\":\"N" + "N".repeat(100)
                + "\",\"recipient_phone\":\"0912345678\",\"address_line\":\"A\"}")));
        assertInvalid(() -> validator.validateCreate(node("{\"recipient_name\":\"A\",\"recipient_phone\":\"0912345678\","
                + "\"address_line\":\"" + "A".repeat(501) + "\"}")));
    }

    private JsonNode node(String json) throws Exception { return mapper.readTree(json); }

    private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(ApiOperationException.class)
                .extracting(ex -> ((ApiOperationException) ex).code()).isEqualTo("INVALID_REQUEST");
    }
}
