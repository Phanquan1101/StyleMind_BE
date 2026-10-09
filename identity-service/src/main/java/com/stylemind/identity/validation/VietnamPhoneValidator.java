package com.stylemind.identity.validation;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import com.stylemind.identity.errors.ApiOperationException;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public final class VietnamPhoneValidator {
    private static final PhoneNumberUtil UTIL = PhoneNumberUtil.getInstance();
    private static final Pattern ALLOWED = Pattern.compile("[0-9+(). \\-]+", Pattern.UNICODE_CHARACTER_CLASS);

    public String canonicalize(String raw) {
        if (raw == null || raw.codePointCount(0, raw.length()) > 64) {
            throw invalid();
        }
        String value = trimUnicode(raw);
        if (value.isEmpty() || !ALLOWED.matcher(value).matches()
                || (value.indexOf('+') >= 0 && (value.indexOf('+') != 0 || value.lastIndexOf('+') != 0))) {
            throw invalid();
        }
        try {
            PhoneNumber number = UTIL.parse(value, "VN");
            if (!UTIL.isValidNumberForRegion(number, "VN") || !"VN".equals(UTIL.getRegionCodeForNumber(number))) {
                throw invalid();
            }
            return UTIL.format(number, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException exception) {
            throw invalid();
        }
    }

    private static ApiOperationException invalid() {
        return new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request is invalid", 0);
    }

    private static String trimUnicode(String value) {
        int start = 0;
        int end = value.length();
        while (start < end) {
            int cp = value.codePointAt(start);
            if (!Character.isWhitespace(cp) && !Character.isSpaceChar(cp)) break;
            start += Character.charCount(cp);
        }
        while (start < end) {
            int cp = value.codePointBefore(end);
            if (!Character.isWhitespace(cp) && !Character.isSpaceChar(cp)) break;
            end -= Character.charCount(cp);
        }
        return value.substring(start, end);
    }
}
