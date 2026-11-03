package com.stylemind.identity.errors;

import org.springframework.http.HttpStatus;

/** Stable Identity API error codes and their HTTP status contract. */
public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Request is missing or malformed"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method is not allowed"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Media type is not supported"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "An account with this email already exists"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email or password is invalid"),
    ACCOUNT_NOT_ACTIVE(HttpStatus.FORBIDDEN, "Account is not eligible to sign in"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh token is invalid"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource was not found"),
    EMAIL_DELIVERY_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "Email delivery failed with a definite rejection"),
    EMAIL_DELIVERY_UNKNOWN(HttpStatus.GATEWAY_TIMEOUT, "Email delivery could not be confirmed"),
    EMAIL_VERIFICATION_INVALID(HttpStatus.BAD_REQUEST, "Email verification could not be completed"),
    EMAIL_VERIFICATION_EXPIRED(HttpStatus.BAD_REQUEST, "The verification code has expired"),
    EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Email verification is temporarily locked"),
    EMAIL_VERIFICATION_SEND_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "A new verification email cannot be sent yet"),
    EMAIL_ALREADY_VERIFIED(HttpStatus.CONFLICT, "The email address is already verified"),
    EMAIL_VERIFICATION_NOT_ALLOWED(HttpStatus.CONFLICT, "Email verification is not allowed for this account"),
    PASSWORD_RESET_VERIFICATION_INVALID(HttpStatus.BAD_REQUEST, "Password reset verification could not be completed"),
    PASSWORD_RESET_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Password reset verification is temporarily locked"),
    PASSWORD_RESET_INVALID(HttpStatus.BAD_REQUEST, "Password reset could not be completed"),
    DEFAULT_ADDRESS_CHANGE_REQUIRED(HttpStatus.CONFLICT, "Choose another default address before deleting this address"),
    ADMIN_ACCESS_DENIED(HttpStatus.FORBIDDEN, "Current account is not eligible to manage users"),
    ACCOUNT_STATUS_TRANSITION_NOT_ALLOWED(HttpStatus.CONFLICT, "The account status transition is not allowed"),
    ADMIN_SELF_CHANGE_FORBIDDEN(HttpStatus.CONFLICT, "An Admin cannot change their own status or roles"),
    LAST_QUALIFYING_ADMIN_REQUIRED(HttpStatus.CONFLICT, "At least one other active and verified Admin is required"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return name();
    }
}
