package com.stylemind.identity.errors;

public final class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Refresh token is invalid");
    }
}
