package com.stylemind.stylist.errors;

import java.util.UUID;

public class StylistApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final UUID requestId;

    public StylistApiException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public StylistApiException(ErrorCode errorCode, UUID requestId) {
        super(errorCode.detail());
        this.errorCode = errorCode;
        this.requestId = requestId;
    }

    public ErrorCode errorCode() { return errorCode; }
    public UUID requestId() { return requestId; }
}
