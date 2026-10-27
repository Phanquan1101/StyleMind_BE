package com.stylemind.payment.errors;

import org.springframework.http.HttpStatus;

public class PaymentApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String detail;

    public PaymentApiException(HttpStatus status, String code, String detail) {
        super(detail);
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
