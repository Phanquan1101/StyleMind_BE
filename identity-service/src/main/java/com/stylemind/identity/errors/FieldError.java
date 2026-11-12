package com.stylemind.identity.errors;

/** One invalid request field, reported to clients as an element of the problem+json "errors" array. */
public record FieldError(String field, String code, String message) {
}
