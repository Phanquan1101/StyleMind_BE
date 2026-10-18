package com.stylemind.identity.service.session;

import com.stylemind.identity.dto.login.LoginResponse;

public interface RefreshSessionService {
    LoginResponse refresh(String refreshToken);
    void logout(String refreshToken);
}
