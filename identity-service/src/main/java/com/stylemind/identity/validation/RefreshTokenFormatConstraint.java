package com.stylemind.identity.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = RefreshTokenFormatValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface RefreshTokenFormatConstraint {
    String message() default "must be a valid opaque refresh token";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
