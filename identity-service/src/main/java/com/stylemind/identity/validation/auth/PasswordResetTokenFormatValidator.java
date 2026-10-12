package com.stylemind.identity.validation.auth;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordResetTokenFormatValidator
        implements ConstraintValidator<PasswordResetTokenFormatConstraint, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return PasswordResetTokenFormat.isValid(value);
    }
}
