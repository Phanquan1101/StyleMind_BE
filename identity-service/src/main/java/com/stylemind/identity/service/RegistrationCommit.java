package com.stylemind.identity.service;

import com.stylemind.identity.dto.RegisterResponse;

public record RegistrationCommit(RegisterResponse response, EmailDispatch dispatch) {

    @Override
    public String toString() {
        return "RegistrationCommit[response=" + response + ", dispatch=[REDACTED]]";
    }
}
