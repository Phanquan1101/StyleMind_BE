package com.stylemind.identity.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class RefreshTokenFormatValidator implements ConstraintValidator<RefreshTokenFormatConstraint, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return RefreshTokenFormat.isValid(value);
    }
}
