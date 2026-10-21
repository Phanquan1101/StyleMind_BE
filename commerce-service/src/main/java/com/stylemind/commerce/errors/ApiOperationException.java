package com.stylemind.commerce.errors;

import org.springframework.http.HttpStatus;

public final class ApiOperationException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String detail;

    public ApiOperationException(HttpStatus status, String code, String detail) {
        super(code);
        this.status = status;
        this.code = code;
        this.detail = detail;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String detail() {
        return detail;
    }
}
