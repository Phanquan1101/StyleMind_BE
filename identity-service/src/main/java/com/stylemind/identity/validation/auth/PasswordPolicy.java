package com.stylemind.identity.validation.auth;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = PasswordPolicyValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordPolicy {

    String message() default "Mật khẩu phải có từ 12 ký tự trở lên (tối đa 72 byte)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
