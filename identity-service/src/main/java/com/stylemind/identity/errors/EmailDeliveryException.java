package com.stylemind.identity.errors;

import org.springframework.http.HttpStatus;

public final class EmailDeliveryException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public EmailDeliveryException(HttpStatus status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
