package com.stylemind.identity.model.register;

import com.stylemind.identity.client.notification.payload.EmailDispatch;
import com.stylemind.identity.dto.register.RegisterResponse;

public record RegistrationCommit(RegisterResponse response, EmailDispatch dispatch) {

    @Override
    public String toString() {
        return "RegistrationCommit[response=" + response + ", dispatch=[REDACTED]]";
    }
}
