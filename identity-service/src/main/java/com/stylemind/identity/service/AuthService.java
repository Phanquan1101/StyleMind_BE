package com.stylemind.identity.service;

import com.stylemind.identity.dto.login.LoginRequest;
import com.stylemind.identity.dto.login.LoginResponse;
import com.stylemind.identity.dto.register.RegisterRequest;
import com.stylemind.identity.dto.register.RegisterResponse;

public interface AuthService {
    RegisterResponse register(RegisterRequest request);
    LoginResponse login(LoginRequest request);
    LoginResponse refresh(String refreshToken);
    void logout(String refreshToken);
}
