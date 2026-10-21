package com.stylemind.commerce.dto.order;

import java.util.UUID;

public record AddressSnapshot(UUID sourceAddressId, String recipientName, String recipientPhone, String addressLine) {
}
