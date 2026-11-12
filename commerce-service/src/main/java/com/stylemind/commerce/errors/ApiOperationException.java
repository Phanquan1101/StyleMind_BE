package com.stylemind.commerce.errors;

import java.util.List;
import org.springframework.http.HttpStatus;

public final class ApiOperationException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String detail;
    private final List<FieldError> fieldErrors;

    public ApiOperationException(HttpStatus status, String code, String detail) {
        this(status, code, detail, List.of());
    }

    public ApiOperationException(HttpStatus status, String code, String detail, List<FieldError> fieldErrors) {
        super(code);
        this.status = status;
        this.code = code;
        this.detail = detail;
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    /** 400 INVALID_REQUEST that also tells the client which fields are wrong. */
    public static ApiOperationException invalidFields(List<FieldError> fieldErrors) {
        return new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "Request is missing or malformed", fieldErrors);
    }

    public List<FieldError> fieldErrors() {
        return fieldErrors;
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
