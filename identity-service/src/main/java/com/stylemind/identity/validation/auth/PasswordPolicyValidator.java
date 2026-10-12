package com.stylemind.identity.validation.auth;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class PasswordPolicyValidator implements ConstraintValidator<PasswordPolicy, String> {

    private static final int MIN_CODE_POINTS = 12;
    private static final int MAX_UTF8_BYTES = 72;

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return true;
        }
        return password.codePointCount(0, password.length()) >= MIN_CODE_POINTS
                && password.getBytes(StandardCharsets.UTF_8).length <= MAX_UTF8_BYTES;
    }
}
