package com.stylemind.stylist.errors;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Request is missing or malformed"),
    INVALID_CONTEXT(HttpStatus.BAD_REQUEST, "A styling need or context is required"),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "History cursor is invalid"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource was not found"),
    SESSION_CLOSED(HttpStatus.CONFLICT, "Stylist session is closed"),
    GENERATION_IN_PROGRESS(HttpStatus.CONFLICT, "Generation is already in progress for this request"),
    GENERATION_ALREADY_ATTEMPTED(HttpStatus.CONFLICT, "Generation was already attempted for this request"),
    GENERATION_PROVIDER_REFUSED(HttpStatus.UNPROCESSABLE_ENTITY, "The recommendation provider refused this request"),
    GENERATION_INPUT_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "The assembled recommendation context exceeds the input token budget"),
    GENERATION_PROVIDER_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "A recommendation dependency exceeded its deadline"),
    GENERATION_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "A recommendation dependency is unavailable"),
    GENERATION_PROVIDER_PROTOCOL_ERROR(HttpStatus.BAD_GATEWAY, "A recommendation dependency returned an invalid response"),
    GENERATION_RUNTIME_UNCONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "Stylist generation is not configured"),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Request body exceeds the allowed size"),
    DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "A required catalog or retrieval dependency is unavailable"),
    DEPENDENCY_PROTOCOL_ERROR(HttpStatus.BAD_GATEWAY, "A dependency returned an invalid response");

    private final HttpStatus status;
    private final String detail;

    ErrorCode(HttpStatus status, String detail) {
        this.status = status;
        this.detail = detail;
    }

    public HttpStatus status() { return status; }
    public String detail() { return detail; }
}
