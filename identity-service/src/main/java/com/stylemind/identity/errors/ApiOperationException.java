package com.stylemind.identity.errors;

import com.stylemind.identity.entity.Account;

public final class ApiOperationException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String detail;
    private final long retryAfterSeconds;
    private final boolean accountCreated;

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
        super(errorCode.code());
        this.errorCode = errorCode;
        this.detail = detail;
        this.retryAfterSeconds = retryAfterSeconds;
        this.accountCreated = accountCreated;
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
}
