package com.stylemind.identity.errors;

import com.stylemind.identity.entity.Account;
import java.util.List;

public final class ApiOperationException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String detail;
    private final long retryAfterSeconds;
    private final boolean accountCreated;
    private final List<FieldError> fieldErrors;

    public ApiOperationException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    public ApiOperationException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, 0, false);
    }

    public ApiOperationException(ErrorCode errorCode, String detail, long retryAfterSeconds) {
        this(errorCode, detail, retryAfterSeconds, false);
    }

    private ApiOperationException(ErrorCode errorCode, String detail, long retryAfterSeconds,
            boolean accountCreated) {
        this(errorCode, detail, retryAfterSeconds, accountCreated, List.of());
    }

    private ApiOperationException(ErrorCode errorCode, String detail, long retryAfterSeconds,
            boolean accountCreated, List<FieldError> fieldErrors) {
        super(errorCode.code());
        this.errorCode = errorCode;
        this.detail = detail;
        this.retryAfterSeconds = retryAfterSeconds;
        this.accountCreated = accountCreated;
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    /** 400 INVALID_REQUEST that also tells the client which fields are wrong. */
    public static ApiOperationException invalidFields(List<FieldError> fieldErrors) {
        return new ApiOperationException(ErrorCode.INVALID_REQUEST, "Request is missing or malformed", 0, false, fieldErrors);
    }

    public static ApiOperationException registrationDeliveryFailure(ApiOperationException failure) {
        if (failure.errorCode != ErrorCode.EMAIL_DELIVERY_FAILED
                && failure.errorCode != ErrorCode.EMAIL_DELIVERY_UNKNOWN) {
            throw new IllegalArgumentException("Registration delivery failure requires a delivery error code");
        }
        return new ApiOperationException(failure.errorCode,
                "Account was created and remains PENDING_VERIFICATION. Request a new verification email.", 0, true);
    }

    public ErrorCode errorCode() { return errorCode; }
    public org.springframework.http.HttpStatus status() { return errorCode.status(); }
    public String code() { return errorCode.code(); }
    public String detail() { return detail; }
    public long retryAfterSeconds() { return retryAfterSeconds; }
    public boolean accountCreated() { return accountCreated; }
    public List<FieldError> fieldErrors() { return fieldErrors; }
}
