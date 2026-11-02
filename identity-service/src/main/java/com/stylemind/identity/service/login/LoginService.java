package com.stylemind.identity.service.login;

import com.stylemind.identity.dto.login.LoginRequest;
import com.stylemind.identity.dto.login.LoginResponse;

public interface LoginService {
    LoginResponse login(LoginRequest request);
}
