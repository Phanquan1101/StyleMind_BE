package com.stylemind.identity.service.emailverification;

import com.stylemind.identity.model.emailverification.VerificationOutcome;

public interface EmailVerificationService {
    VerificationOutcome verify(String email, String code);
    VerificationOutcome resend(String email);
    void requireSuccess(VerificationOutcome outcome);
}
