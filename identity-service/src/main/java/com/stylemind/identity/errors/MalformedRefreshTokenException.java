package com.stylemind.identity.errors;

public final class MalformedRefreshTokenException extends RuntimeException {

    public MalformedRefreshTokenException() {
        super("Refresh token request is invalid");
    }
}
