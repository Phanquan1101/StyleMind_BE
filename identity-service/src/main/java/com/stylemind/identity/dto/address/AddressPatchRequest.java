package com.stylemind.identity.dto.address;

/** Validated patch payload; presence flags preserve omitted versus explicit-null fields. */
public record AddressPatchRequest(
        boolean labelPresent,
        String label,
        boolean recipientNamePresent,
        String recipientName,
        boolean recipientPhonePresent,
        String recipientPhone,
        boolean addressLinePresent,
        String addressLine,
        boolean wardCodePresent,
        String wardCode) {
}
