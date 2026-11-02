package com.stylemind.identity.validation.auth;

import java.util.Base64;

public final class RefreshTokenFormat {

    private RefreshTokenFormat() {
    }

    public static boolean isValid(String value) {
        if (value == null || value.length() != 43) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!((character >= 'A' && character <= 'Z')
                    || (character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9')
                    || character == '-' || character == '_')) {
                return false;
            }
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            return decoded.length == 32
                    && Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
