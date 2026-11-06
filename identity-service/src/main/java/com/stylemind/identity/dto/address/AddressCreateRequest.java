package com.stylemind.identity.dto.address;

/** Validated address create payload passed from the HTTP adapter to the service. */
public record AddressCreateRequest(
        String label,
        String recipientName,
        String recipientPhone,
        String addressLine,
        String wardCode) {
}
