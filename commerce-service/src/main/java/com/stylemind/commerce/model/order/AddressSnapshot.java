package com.stylemind.commerce.model.order;

import java.util.UUID;

public record AddressSnapshot(UUID sourceAddressId, String recipientName, String recipientPhone, String addressLine) {
}
