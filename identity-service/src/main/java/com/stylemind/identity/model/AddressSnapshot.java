package com.stylemind.identity.model;

import java.util.UUID;

public record AddressSnapshot(UUID addressId, String label, String recipientName, String recipientPhone,
        String addressLine, String wardCode, boolean isDefault) {
}
