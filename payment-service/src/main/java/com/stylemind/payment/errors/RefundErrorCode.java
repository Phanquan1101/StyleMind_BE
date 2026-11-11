package com.stylemind.payment.errors;

import org.springframework.http.HttpStatus;

public enum RefundErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Refund request is invalid"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "You do not have access to this refund action"),
    REFUND_NOT_FOUND(HttpStatus.NOT_FOUND, "Refund or eligible Commerce source was not found"),
    REFUND_STATE_CONFLICT(HttpStatus.CONFLICT, "The refund is not in the required state"),
    REFUND_LEDGER_MISMATCH(HttpStatus.CONFLICT, "No matching settled payment transaction was found"),
    REFUND_AMOUNT_EXCEEDED(HttpStatus.CONFLICT, "Refund allocation would exceed the settled payment amount"),
    REFUND_SOURCE_CHANGED(HttpStatus.CONFLICT, "Refund source does not match the existing refund"),
    REFUND_TRANSFER_REFERENCE_IN_USE(HttpStatus.CONFLICT, "The transfer reference is already in use"),
    REFUND_DESTINATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Refund destination encryption is unavailable"),
    COMMERCE_REFUND_SOURCE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Commerce refund source lookup is not configured"),
    COMMERCE_REFUND_SOURCE_INVALID(HttpStatus.BAD_GATEWAY, "Commerce refund source is invalid"),
    COMMERCE_REFUND_SOURCE_LOOKUP_FAILED(HttpStatus.BAD_GATEWAY, "Commerce refund source lookup failed");

    private final HttpStatus status;
    private final String detail;

    RefundErrorCode(HttpStatus status, String detail) {
        this.status = status;
        this.detail = detail;
    }

    public PaymentApiException exception() {
        return new PaymentApiException(status, name(), detail);
    }
}
