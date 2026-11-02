package com.stylemind.identity.service.passwordreset;

import com.stylemind.identity.dto.passwordreset.ForgotPasswordResponse;
import com.stylemind.identity.dto.passwordreset.ResetTokenResponse;

public interface PasswordResetService {
    ForgotPasswordResponse requestOtp(String email);
    ResetTokenResponse verifyOtp(String email, String code);
    void resetPassword(String resetToken, String newPassword);
}
