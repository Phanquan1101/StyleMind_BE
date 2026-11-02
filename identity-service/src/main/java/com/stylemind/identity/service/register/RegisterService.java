package com.stylemind.identity.service.register;

import com.stylemind.identity.dto.register.RegisterRequest;
import com.stylemind.identity.dto.register.RegisterResponse;

public interface RegisterService {
    RegisterResponse register(RegisterRequest request);
}
