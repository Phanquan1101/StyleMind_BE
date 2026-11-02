package com.stylemind.identity.errors;

import org.springframework.http.HttpStatus;

public final class ApiOperationException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String detail;
    private final long retryAfterSeconds;
    private final boolean accountCreated;

    public ApiOperationException(HttpStatus status, String code, String detail, long retryAfterSeconds) {
        this(status, code, detail, retryAfterSeconds, false);
    }

    private ApiOperationException(HttpStatus status, String code, String detail, long retryAfterSeconds,
            boolean accountCreated) {
        super(code);
        this.status = status;
        this.code = code;
        this.detail = detail;
        this.retryAfterSeconds = retryAfterSeconds;
        this.accountCreated = accountCreated;
    }

    public static ApiOperationException registrationDeliveryFailure(EmailDeliveryException failure) {
        return new ApiOperationException(failure.status(), failure.code(),
                "Account was created and remains PENDING_VERIFICATION. Request a new verification email.", 0, true);
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public String detail() { return detail; }
    public long retryAfterSeconds() { return retryAfterSeconds; }
    public boolean accountCreated() { return accountCreated; }
}
