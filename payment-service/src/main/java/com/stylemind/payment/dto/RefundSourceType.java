package com.stylemind.payment.dto;

import com.stylemind.payment.errors.RefundErrorCode;
import java.util.UUID;

public enum RefundSourceType {
    RETURN, CANCELLATION;

    public static UUID parseId(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (id.toString().equalsIgnoreCase(value)) {
                return id;
            }
        } catch (IllegalArgumentException exception) {
            // Give invalid identifiers the same public error.
        }
        throw RefundErrorCode.INVALID_REQUEST.exception();
    }
}
