package com.stylemind.identity.dto.register;

import com.stylemind.identity.validation.auth.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Locale;

public record RegisterRequest(
        @NotBlank(message = "Vui lòng nhập email") @Email(message = "Email không hợp lệ") String email,
        @NotBlank(message = "Vui lòng nhập mật khẩu") @PasswordPolicy String password) {

    public RegisterRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "RegisterRequest[email=[REDACTED], password=[REDACTED]]";
    }
}
