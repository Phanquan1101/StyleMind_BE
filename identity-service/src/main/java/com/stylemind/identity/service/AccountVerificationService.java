package com.stylemind.identity.service;

import com.stylemind.identity.dto.passwordreset.ForgotPasswordResponse;
import com.stylemind.identity.dto.passwordreset.ResetTokenResponse;

public interface AccountVerificationService {
    void verifyEmail(String email, String code);
    void resendEmailVerification(String email);
    ForgotPasswordResponse requestPasswordReset(String email);
    ResetTokenResponse verifyPasswordResetOtp(String email, String code);
    void resetPassword(String resetToken, String newPassword);
}
