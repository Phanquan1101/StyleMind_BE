package com.stylemind.identity.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class MaxUtf8BytesValidator implements ConstraintValidator<MaxUtf8Bytes, String> {

    private int maximumBytes;

    @Override
    public void initialize(MaxUtf8Bytes constraintAnnotation) {
        maximumBytes = constraintAnnotation.value();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.getBytes(StandardCharsets.UTF_8).length <= maximumBytes;
    }
}
